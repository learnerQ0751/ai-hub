package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.api.EmbeddingService;
import com.qtua.aihub.api.model.req.EmbeddingReqDTO;
import com.qtua.aihub.api.model.res.EmbeddingResDTO;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * OpenAI 兼容 Embedding 入口（数据面，任意有效 Key）。
 *
 * <p>治理由外层 GatewayGovernanceFilter 统一完成（callType=EMBEDDING）；
 * Controller 只回填实际 usage，供治理层按 embedding 单价计价与结算。</p>
 */
@RestController
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    /**
     * 构造控制器。
     *
     * @param embeddingService 向量化服务
     */
    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 文本向量化。
     *
     * @param reqDTO  请求
     * @param request HTTP 请求（读取治理上下文）
     * @return OpenAI 兼容响应
     */
    @PostMapping(path = {"/v1/embeddings", "/embeddings"}, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public EmbeddingResDTO embed(@RequestBody(required = false) EmbeddingReqDTO reqDTO,
                                 HttpServletRequest request) {
        EmbeddingResDTO response = embeddingService.embed(reqDTO);
        GatewayContext context =
                (GatewayContext) request.getAttribute(GatewayContext.ATTRIBUTE);
        if (Objects.nonNull(context) && Objects.nonNull(response.usage())) {
            long promptTokens = Math.max(0L, response.usage().promptTokens());
            int tokens = (int) Math.min(Integer.MAX_VALUE, promptTokens);
            context.complete(TokenUsage.of(tokens, 0), response.model());
        }
        return response;
    }
}
