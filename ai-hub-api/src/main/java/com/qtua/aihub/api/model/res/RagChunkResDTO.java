package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 切片视图（§7.1 文档切片列表）。
 *
 * @param chunkId     切片 ID
 * @param docId       文档 ID
 * @param projectId   项目 ID
 * @param chunkIndex  切片序号（文档内从 0 递增）
 * @param content     切片文本
 * @param charCount   字符数
 * @param source      原始文件名
 * @param indexStatus 索引状态
 * @param createdAt   创建时间（ISO-8601）
 */
public record RagChunkResDTO(@JsonProperty("chunk_id") String chunkId,
                             @JsonProperty("doc_id") String docId,
                             @JsonProperty("project_id") String projectId,
                             @JsonProperty("chunk_index") int chunkIndex,
                             String content,
                             @JsonProperty("char_count") int charCount,
                             String source,
                             @JsonProperty("index_status") String indexStatus,
                             @JsonProperty("created_at") String createdAt) {
}
