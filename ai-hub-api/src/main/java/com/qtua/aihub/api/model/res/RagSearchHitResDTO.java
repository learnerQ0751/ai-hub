package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 检索命中（HTTP 契约）。
 *
 * @param chunkId    切片 ID
 * @param docId      文档 ID
 * @param source     来源文件名
 * @param chunkIndex 切片序号
 * @param content    切片正文
 * @param score      向量相似度
 * @param rerankScore 规则重排分数；未重排为 null
 */
public record RagSearchHitResDTO(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("doc_id") String docId,
        @JsonProperty("source") String source,
        @JsonProperty("chunk_index") int chunkIndex,
        @JsonProperty("content") String content,
        @JsonProperty("score") double score,
        @JsonProperty("rerank_score") Double rerankScore) {
}
