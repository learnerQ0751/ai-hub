package com.qtua.aihub.manager.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * RAG 文档业务对象。
 *
 * @param docId               文档 ID
 * @param projectId           项目 ID
 * @param source              原始文件名
 * @param contentType         内容类型
 * @param sizeBytes           文件字节数
 * @param contentHash         文件 SHA-256
 * @param rawContent          原始文件字节
 * @param chunkCount          切片数
 * @param indexStatus         索引状态
 * @param embeddingModel      向量模型标识
 * @param embeddingDimension  向量维度
 * @param embeddingDurationMs 向量化耗时（毫秒）
 * @param embeddingCost       向量化成本
 * @param errorMessage        错误信息
 * @param createdAt           创建时间
 * @param updatedAt           更新时间
 */
public record RagDocumentBO(String docId, String projectId, String source, String contentType,
                            long sizeBytes, String contentHash, byte[] rawContent, int chunkCount,
                            String indexStatus, String embeddingModel, Integer embeddingDimension,
                            Long embeddingDurationMs, BigDecimal embeddingCost, String errorMessage,
                            Instant createdAt, Instant updatedAt) {

    /**
     * 返回原始文件副本。
     *
     * @return 原始文件副本；无原始文件为 null
     */
    @Override
    public byte[] rawContent() {
        return Objects.isNull(rawContent) ? null : rawContent.clone();
    }
}
