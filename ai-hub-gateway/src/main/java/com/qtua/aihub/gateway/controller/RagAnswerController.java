package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.api.RagAnswerService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.RagAnswerReqDTO;
import com.qtua.aihub.api.model.res.RagAnswerResDTO;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import com.qtua.aihub.gateway.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * RAG 生成与引用溯源入口（数据面，任意有效 Key）。
 *
 * <p>治理由外层 GatewayGovernanceFilter 统一完成：callType=RAG，target_name=rag_answer，
 * 入口只做 RPM；Controller 在响应后回填内部 chat 的 usage/deploymentModel 与查询 embedding
 * 用量，治理层按实际用量事后计价、预算校验并写 call_logs。</p>
 */
@RestController
public class RagAnswerController {

    private final RagAnswerService ragAnswerService;

    /**
     * 构造控制器。
     *
     * @param ragAnswerService 生成服务
     */
    public RagAnswerController(RagAnswerService ragAnswerService) {
        this.ragAnswerService = ragAnswerService;
    }

    /**
     * 检索并生成带引用的答案。
     *
     * @param reqDTO  生成参数
     * @param request HTTP 请求（认证与请求追踪上下文）
     * @return 答案、引用与用量
     */
    @PostMapping(path = "/v1/rag/answer", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RagAnswerResDTO answer(@RequestBody(required = false) RagAnswerReqDTO reqDTO,
                                  HttpServletRequest request) {
        KeyAuthDTO auth = (KeyAuthDTO) request.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        RagAnswerResDTO response = ragAnswerService.answer(reqDTO, auth, requestId);
        GatewayContext context =
                (GatewayContext) request.getAttribute(GatewayContext.ATTRIBUTE);
        if (Objects.nonNull(context)) {
            TokenUsage usage = new TokenUsage(response.usage().promptTokens(),
                    response.usage().completionTokens(), response.usage().totalTokens());
            context.complete(usage, response.deploymentModel(), response.model(), 0, false);
            context.setModelAlias(response.model());
            context.setEmbeddingUsage(response.embeddingModel(), response.embeddingTokens());
        }
        return response;
    }
}
