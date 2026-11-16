package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.api.RagSearchService;
import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * RAG 检索入口（数据面，任意有效 Key）。
 *
 * <p>治理由外层 GatewayGovernanceFilter 统一完成：callType=RAG，
 * target_name=search_knowledge，只做 RPM；查询 embedding 成本记录到 call_logs。</p>
 */
@RestController
public class RagSearchController {

    private final RagSearchService ragSearchService;

    /**
     * 构造控制器。
     *
     * @param ragSearchService 检索服务
     */
    public RagSearchController(RagSearchService ragSearchService) {
        this.ragSearchService = ragSearchService;
    }

    /**
     * 检索知识切片。
     *
     * @param reqDTO  检索参数
     * @param request HTTP 请求（读取治理上下文）
     * @return 命中结果（无命中 no_hit=true，HTTP 200）
     */
    @PostMapping(path = "/v1/rag/search", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RagSearchResDTO search(@RequestBody(required = false) RagSearchReqDTO reqDTO,
                                  HttpServletRequest request) {
        RagSearchResDTO response = ragSearchService.search(reqDTO);
        GatewayContext context =
                (GatewayContext) request.getAttribute(GatewayContext.ATTRIBUTE);
        if (Objects.nonNull(context)) {
            context.setEmbeddingUsage(response.embeddingModel(), response.embeddingTokens());
        }
        return response;
    }
}
