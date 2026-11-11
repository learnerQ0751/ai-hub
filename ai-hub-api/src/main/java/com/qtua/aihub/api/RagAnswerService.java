package com.qtua.aihub.api;

import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.RagAnswerReqDTO;
import com.qtua.aihub.api.model.res.RagAnswerResDTO;

/**
 * RAG 生成与引用溯源服务。
 *
 * <p>检索复用 {@link RagSearchService} 语义；生成通过内部 chat 服务调用模型，不经 HTTP/Filter。
 * 无召回默认返回兜底话术且不调用模型；自由回答需服务端开关与请求同时允许。</p>
 */
public interface RagAnswerService {

    /**
     * 检索并生成带引用的答案。
     *
     * @param reqDTO    生成请求
     * @param auth      认证上下文
     * @param requestId 请求追踪 ID（与入口日志一致）
     * @return 答案、引用与用量
     */
    RagAnswerResDTO answer(RagAnswerReqDTO reqDTO, KeyAuthDTO auth, String requestId);
}
