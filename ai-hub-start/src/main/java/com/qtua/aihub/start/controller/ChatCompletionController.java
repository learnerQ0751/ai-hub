package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import com.qtua.aihub.start.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * OpenAI 兼容 Chat Completions HTTP 入口。
 *
 * <p>只负责 HTTP 契约与响应包装，不包含业务编排；业务逻辑在 service/core。</p>
 */
@RestController
public class ChatCompletionController {

    private final ChatCompletionService chatCompletionService;

    /**
     * 构造控制器。
     *
     * @param chatCompletionService 聊天补全应用服务
     */
    public ChatCompletionController(ChatCompletionService chatCompletionService) {
        this.chatCompletionService = chatCompletionService;
    }

    /**
     * 接收 OpenAI 兼容请求并返回上游响应。
     *
     * <p>同时支持 /v1/chat/completions 与 /chat/completions，兼容不同 SDK 的默认路径。
     * 认证上下文由 AuthFilter 写入请求属性，这里只做透传。</p>
     *
     * @param requestBody 原始 JSON 请求体
     * @param servletRequest HTTP 请求（读取认证上下文）
     * @return 上游 ChatCompletion JSON（原样透传，仅约定字段改写）
     */
    @PostMapping(path = {"/v1/chat/completions", "/chat/completions"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody String requestBody, HttpServletRequest servletRequest) {
        KeyAuthDTO auth = (KeyAuthDTO) servletRequest.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        if (Objects.isNull(auth)) {
            // 过滤器未执行说明链路异常，按未鉴权处理，绝不默认放行。
            throw ChatCompletionException.invalidApiKey();
        }
        String requestId = (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE);
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
}
