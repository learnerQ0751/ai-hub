package com.qtua.aihub.common.rag;

import java.util.List;

/**
 * 一次 embedding 调用的结果。
 *
 * <p>携带向量与本次输入 token 数：供应商返回 usage 时用真实值，否则由
 * {@code EmbeddingTokenEstimator} 估算。token 数用于计价与调用日志。</p>
 *
 * @param vectors     向量列表（顺序与入参一致）
 * @param inputTokens 输入 token 数
 */
public record EmbeddingResult(List<float[]> vectors, long inputTokens) {

    /**
     * 防御性复制向量列表并校验 token 非负。
     */
    public EmbeddingResult {
        vectors = List.copyOf(vectors);
        if (inputTokens < 0) {
            throw new IllegalArgumentException("embedding input tokens must not be negative");
        }
    }
}
