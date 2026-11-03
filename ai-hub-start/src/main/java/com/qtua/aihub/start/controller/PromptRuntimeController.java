package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.PromptChatService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.PromptChatReqDTO;
import com.qtua.aihub.api.model.req.PromptRenderReqDTO;
import com.qtua.aihub.api.model.res.PromptChatResDTO;
import com.qtua.aihub.api.model.res.PromptRenderResDTO;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import com.qtua.aihub.start.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Prompt 数据面入口：渲染（不调模型）与对话（渲染 → chat 链路）。
 *
 * <p>render 只做渲染；chat 由外层 GatewayGovernanceFilter 统一治理一次
 * （RPM/TPM/预算/计价/日志），内部 chat 调用不再经过 Filter。</p>
 */
@RestController
public class PromptRuntimeController {

    private static final String CALL_TYPE_PROMPT = "PROMPT";

    private final PromptChatService promptChatService;

    /**
     * 构造控制器。
     *
     * @param promptChatService Prompt 数据面服务
     */
    public PromptRuntimeController(PromptChatService promptChatService) {
        this.promptChatService = promptChatService;
    }

    /**
     * 渲染模板（不调用模型）。
     *
     * @param name   模板名
     * @param reqDTO 渲染参数
     * @return 渲染结果
     */
    @PostMapping(path = "/v1/prompts/{name}/render",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PromptRenderResDTO render(@PathVariable("name") String name,
                                     @RequestBody(required = false) PromptRenderReqDTO reqDTO) {
        PromptRenderReqDTO request = Objects.isNull(reqDTO)
                ? new PromptRenderReqDTO(null, java.util.Map.of()) : reqDTO;
        return promptChatService.render(name, request);
    }

    /**
     * 渲染并调用模型。
     *
     * @param name      模板名
     * @param reqDTO    对话参数
     * @param request   HTTP 请求（认证与治理上下文）
     * @return 上游原始响应 JSON
     */
    @PostMapping(path = "/v1/prompts/{name}/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@PathVariable("name") String name,
                                       @RequestBody PromptChatReqDTO reqDTO,
                                       HttpServletRequest request) {
        KeyAuthDTO auth = (KeyAuthDTO) request.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        PromptChatResDTO result = promptChatService.chat(name, reqDTO, auth, requestId);
        GatewayContext context = (GatewayContext) request.getAttribute(GatewayContext.ATTRIBUTE);
        if (Objects.nonNull(context)) {
            // 日志精确到实际使用的模板版本。
            context.setCallTarget(CALL_TYPE_PROMPT,
                    result.promptName() + "@v" + result.version());
            context.complete(result.usage(), result.deploymentModel(),
                    result.servedAlias(), result.retryCount(), result.fallbackUsed());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.rawResponse());
    }
}
