package com.qtua.aihub.start.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.ChatCompletionStreamResDTO;
import com.qtua.aihub.api.model.res.ErrorResponse;
import com.qtua.aihub.common.constant.JsonFields;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.service.gateway.GatewayGovernanceService;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import com.qtua.aihub.start.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI 兼容 Chat Completions HTTP 入口。
 *
 * <p>只负责 HTTP 契约与响应包装；业务逻辑在 service/core。
 * 非流式请求返回上游原始 JSON；流式请求以 SSE 逐块透传，
 * 并在流结束/失败时回调网关治理服务完成计量与日志。</p>
 */
@RestController
public class ChatCompletionController {

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private final ChatCompletionService chatCompletionService;
    private final GatewayGovernanceService governanceService;
    private final ObjectMapper objectMapper;

    /**
     * 构造控制器。
     *
     * @param chatCompletionService 聊天补全应用服务
     * @param governanceService     网关治理服务（流式收尾）
     * @param objectMapper          JSON 解析/序列化器
     */
    public ChatCompletionController(ChatCompletionService chatCompletionService,
                                    GatewayGovernanceService governanceService,
                                    ObjectMapper objectMapper) {
        this.chatCompletionService = chatCompletionService;
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 接收 OpenAI 兼容请求并返回上游响应。
     *
     * <p>同时支持 /v1/chat/completions 与 /chat/completions，兼容不同 SDK 的默认路径。
     * 认证上下文由 AuthFilter 写入请求属性，这里只做透传。</p>
     *
     * @param requestBody   原始 JSON 请求体
     * @param servletRequest HTTP 请求（读取认证上下文）
     * @return 非流式为上游 ChatCompletion JSON；流式为 SSE
     */
    @PostMapping(path = {"/v1/chat/completions", "/chat/completions"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> chat(@RequestBody String requestBody,
                                  HttpServletRequest servletRequest) {
        KeyAuthDTO auth = (KeyAuthDTO) servletRequest.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        if (Objects.isNull(auth)) {
            // 过滤器未执行说明链路异常，按未鉴权处理，绝不默认放行。
            throw ChatCompletionException.invalidApiKey();
        }
        String requestId = (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (isStreaming(requestBody)) {
            return stream(requestBody, auth, requestId, servletRequest);
        }
        ChatCompletionResDTO resDTO = chatCompletionService.chat(
                new ChatCompletionReqDTO(requestBody, auth, requestId));
        GatewayContext context = (GatewayContext) servletRequest.getAttribute(GatewayContext.ATTRIBUTE);
        if (Objects.nonNull(context)) {
            context.complete(resDTO.getUsage(), resDTO.getDeploymentModel(),
                    resDTO.getServedAlias(), resDTO.getRetryCount(), resDTO.isFallbackUsed());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resDTO.getRawResponse());
    }

    /**
     * 判断请求是否要求流式响应；非法 JSON 留给业务层产生标准错误。
     *
     * @param requestBody 原始请求体
     * @return true 表示流式
     */
    private boolean isStreaming(String requestBody) {
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            return !ObjectUtils.isEmpty(root) && root.path(JsonFields.STREAM).asBoolean(false);
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    /**
     * 处理流式请求：订阅上游 SSE 并逐块写出，结束后结算治理。
     *
     * @param requestBody    原始请求体
     * @param auth           认证上下文
     * @param requestId      请求追踪 ID
     * @param servletRequest HTTP 请求（读取治理上下文）
     * @return SSE 响应
     */
    private ResponseEntity<SseEmitter> stream(String requestBody, KeyAuthDTO auth,
                                              String requestId,
                                              HttpServletRequest servletRequest) {
        ChatCompletionStreamResDTO resDTO = chatCompletionService.chatStream(
                new ChatCompletionReqDTO(requestBody, auth, requestId));
        GatewayContext context = (GatewayContext) servletRequest.getAttribute(GatewayContext.ATTRIBUTE);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        subscription.set(resDTO.getEvents().subscribe(
                data -> sendEvent(emitter, data, subscription),
                error -> failStream(emitter, context, error),
                () -> completeStream(emitter, context, resDTO)));
        emitter.onCompletion(() -> dispose(subscription));
        emitter.onTimeout(() -> {
            dispose(subscription);
            failStream(emitter, context, ChatCompletionException.upstreamTimeout(
                    new IllegalStateException("stream timeout")));
        });
        emitter.onError(error -> dispose(subscription));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    /**
     * 发送单个 SSE data 事件；客户端断开时取消上游订阅。
     *
     * @param emitter      SSE emitter
     * @param data         data 内容
     * @param subscription 上游订阅引用
     */
    private void sendEvent(SseEmitter emitter, String data,
                           AtomicReference<Disposable> subscription) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException ex) {
            dispose(subscription);
            emitter.completeWithError(ex);
        } catch (IllegalStateException ex) {
            // emitter 已完成（客户端断开/超时），停止上游流。
            dispose(subscription);
        }
    }

    /**
     * 流正常结束：回填 usage 并结算治理。
     *
     * @param emitter SSE emitter
     * @param context 治理上下文
     * @param resDTO  流式响应
     */
    private void completeStream(SseEmitter emitter, GatewayContext context,
                                ChatCompletionStreamResDTO resDTO) {
        if (Objects.nonNull(context)) {
            context.complete(resDTO.getUsage().get(), resDTO.getDeploymentModel(),
                    resDTO.getServedAlias(), 0, false);
            governanceService.complete(context);
        }
        emitter.complete();
    }

    /**
     * 流异常：按 SSE error 事件结束，并记录失败治理。
     *
     * @param emitter SSE emitter
     * @param context 治理上下文
     * @param error   异常
     */
    private void failStream(SseEmitter emitter, GatewayContext context, Throwable error) {
        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        String type = "internal_error";
        String message = "Stream processing failed";
        String param = null;
        String code = "500";
        if (error instanceof ChatCompletionException ex) {
            status = ex.getStatus().value();
            type = ex.getType();
            message = ex.getMessage();
            param = ex.getParam();
            code = ex.getCode();
        }
        if (Objects.nonNull(context)) {
            context.fail(status, type);
            governanceService.fail(context);
        }
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(objectMapper.writeValueAsString(
                            ErrorResponse.of(message, type, param, code))));
        } catch (IOException | IllegalStateException ex) {
            // 客户端已断开，无法再写 error 事件。
        }
        emitter.complete();
    }

    /**
     * 取消上游订阅（幂等）。
     *
     * @param subscription 订阅引用
     */
    private void dispose(AtomicReference<Disposable> subscription) {
        Disposable disposable = subscription.get();
        if (Objects.nonNull(disposable)) {
            disposable.dispose();
        }
    }
}
