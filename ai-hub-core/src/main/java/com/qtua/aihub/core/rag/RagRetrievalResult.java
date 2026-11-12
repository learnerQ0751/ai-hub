package com.qtua.aihub.core.rag;

import java.util.List;
import java.util.Objects;

/**
 * 一次检索的编排结果。
 *
 * @param hits           最终命中（已过滤、按需重排、已截断）
 * @param candidateCount 向量召回候选数
 * @param reranked       是否实际执行了规则重排
 * @param embeddingTokens 查询向量化消耗的输入 token
 * @param embeddingModel  查询向量化使用的模型标识
 */
public record RagRetrievalResult(List<RagRetrievedChunk> hits, int candidateCount,
                                 boolean reranked, long embeddingTokens,
                                 String embeddingModel) {

    /**
     * 防御性复制命中列表；无数据为空列表。
     */
    public RagRetrievalResult {
        hits = Objects.isNull(hits) ? List.of() : List.copyOf(hits);
    }

    /**
     * 兼容构造：无 embedding 计量信息。
     *
     * @param hits           命中
     * @param candidateCount 候选数
     * @param reranked       是否重排
     */
    public RagRetrievalResult(List<RagRetrievedChunk> hits, int candidateCount,
                              boolean reranked) {
        this(hits, candidateCount, reranked, 0L, null);
    }

    /**
     * 是否无命中。
     *
     * @return true 表示没有命中切片
     */
    public boolean noHit() {
        return hits.isEmpty();
    }
}
