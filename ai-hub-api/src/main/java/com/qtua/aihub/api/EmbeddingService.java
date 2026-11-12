package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.EmbeddingReqDTO;
import com.qtua.aihub.api.model.res.EmbeddingResDTO;

/**
 * 文本向量化服务。
 *
 * <p>OpenAI 兼容 `/v1/embeddings` 的业务入口；治理由外层 Filter 统一完成。</p>
 */
public interface EmbeddingService {

    /**
     * 执行向量化。
     *
     * @param reqDTO 请求
     * @return OpenAI 兼容响应
     */
    EmbeddingResDTO embed(EmbeddingReqDTO reqDTO);
}
