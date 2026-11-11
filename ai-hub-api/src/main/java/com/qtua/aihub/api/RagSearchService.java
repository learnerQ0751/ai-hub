package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;

/**
 * RAG 检索服务：按项目隔离的向量检索、元数据过滤、阈值/条数收敛与可选规则重排。
 *
 * <p>无命中返回空列表并标记 `no_hit`，不抛错；跨项目切片不返回。</p>
 */
public interface RagSearchService {

    /**
     * 执行检索。
     *
     * @param reqDTO 检索请求
     * @return 命中结果
     */
    RagSearchResDTO search(RagSearchReqDTO reqDTO);
}
