package com.qtua.aihub.core.rag;

import java.util.List;
import java.util.Objects;

/**
 * 一次检索的编排结果。
 *
 * @param hits           最终命中（已过滤、按需重排、已截断）
 * @param candidateCount 向量召回候选数
 * @param reranked       是否实际执行了规则重排
 */
public record RagRetrievalResult(List<RagRetrievedChunk> hits, int candidateCount,
                                 boolean reranked) {

    /**
     * 防御性复制命中列表；无数据为空列表。
     */
    public RagRetrievalResult {
        hits = Objects.isNull(hits) ? List.of() : List.copyOf(hits);
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
