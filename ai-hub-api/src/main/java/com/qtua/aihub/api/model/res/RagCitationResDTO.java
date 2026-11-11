package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 答案引用（HTTP 契约）。
 *
 * @param index   引用编号（从 1 开始，与答案/prompt 中 `[n]` 对应）
 * @param docId   文档 ID
 * @param chunkId 切片 ID
 * @param source  来源文件名
 * @param score   最终排序分
 */
public record RagCitationResDTO(
        @JsonProperty("index") int index,
        @JsonProperty("doc_id") String docId,
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("source") String source,
        @JsonProperty("score") double score) {
}
