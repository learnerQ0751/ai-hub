package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * RAG 文档视图。
 *
 * <p>JSON 字段使用 snake_case，与 RAG MCP 契约保持一致（REQ-3.6 直接复用）。
 * `duplicated` 表示本次上传命中去重；`reindex_required` 表示当前 embedding 模型
 * 与文档已索引模型不一致，需要重建索引。</p>
 *
 * @param docId               文档 ID
 * @param projectId           项目 ID
 * @param source              原始文件名
 * @param contentType         内容类型
 * @param sizeBytes           文件字节数
 * @param contentHash         文件 SHA-256
 * @param chunkCount          切片数
 * @param indexStatus         索引状态
 * @param embeddingModel      向量模型标识
 * @param embeddingDimension  向量维度
 * @param embeddingDurationMs 向量化耗时（毫秒）
 * @param embeddingCost       向量化成本
 * @param errorMessage        错误信息
 * @param duplicated          是否命中去重
 * @param reindexRequired     是否需要重建索引
 * @param createdAt           创建时间
 * @param updatedAt           更新时间
 */
public record RagDocumentResDTO(
        @JsonProperty("doc_id") String docId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("source") String source,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("size_bytes") long sizeBytes,
        @JsonProperty("content_hash") String contentHash,
        @JsonProperty("chunk_count") int chunkCount,
        @JsonProperty("index_status") String indexStatus,
        @JsonProperty("embedding_model") String embeddingModel,
        @JsonProperty("embedding_dimension") Integer embeddingDimension,
        @JsonProperty("embedding_duration_ms") Long embeddingDurationMs,
        @JsonProperty("embedding_cost") BigDecimal embeddingCost,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("duplicated") boolean duplicated,
        @JsonProperty("reindex_required") boolean reindexRequired,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {
}
