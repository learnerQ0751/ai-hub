package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 检索命中（HTTP 契约）。
 *
 * <p>`score` 保持向量相似度语义；混合检索开启时用 `match_type`、两路原始分数与
 * `rrf_score` 解释排序。`rerank_score` 仅在关闭混合检索且启用规则重排时非空。</p>
 *
 * @param chunkId      切片 ID
 * @param docId        文档 ID
 * @param source       来源文件名
 * @param chunkIndex   切片序号
 * @param content      切片正文
 * @param score        向量相似度；仅字面命中为 0
 * @param rerankScore  规则重排分数；未重排为 null
 * @param matchType    命中路径：vector / keyword / both
 * @param vectorScore  向量路原始分数；未命中为 null
 * @param keywordScore 字面路原始分数（词项覆盖率）；未命中为 null
 * @param rrfScore     RRF 融合分；未启用混合检索为 null
 */
public record RagSearchHitResDTO(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("doc_id") String docId,
        @JsonProperty("source") String source,
        @JsonProperty("chunk_index") int chunkIndex,
        @JsonProperty("content") String content,
        @JsonProperty("score") double score,
        @JsonProperty("rerank_score") Double rerankScore,
        @JsonProperty("match_type") String matchType,
        @JsonProperty("vector_score") Double vectorScore,
        @JsonProperty("keyword_score") Double keywordScore,
        @JsonProperty("rrf_score") Double rrfScore) {

    /**
     * 兼容构造器：三期行为的命中（仅向量路）。
     */
    public RagSearchHitResDTO(String chunkId, String docId, String source, int chunkIndex,
                              String content, double score, Double rerankScore) {
        this(chunkId, docId, source, chunkIndex, content, score, rerankScore,
                "vector", score, null, null);
    }
}
