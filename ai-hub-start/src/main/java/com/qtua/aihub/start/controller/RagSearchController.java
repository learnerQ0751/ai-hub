package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.RagSearchService;
import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 检索入口（数据面，任意有效 Key）。
 *
 * <p>治理由外层 GatewayGovernanceFilter 统一完成：callType=RAG，
 * target_name=search_knowledge，只做 RPM，调用轨迹写 call_logs（cost=0）。</p>
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
     * @param reqDTO 检索参数
     * @return 命中结果（无命中 no_hit=true，HTTP 200）
     */
    @PostMapping(path = "/v1/rag/search", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RagSearchResDTO search(@RequestBody(required = false) RagSearchReqDTO reqDTO) {
        return ragSearchService.search(reqDTO);
    }
}
