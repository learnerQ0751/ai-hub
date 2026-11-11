package com.qtua.aihub.dal.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * `rag_documents` 表数据对象。
 *
 * <p>文档元数据 + 原始文件字节；原始文件用于解析失败留存与重新解析。</p>
 */
public class RagDocumentDO {

    private Long id;
    private String docId;
    private String projectId;
    private String source;
    private String contentType;
    private long sizeBytes;
    private String contentHash;
    private byte[] rawContent;
    private int chunkCount;
    private String indexStatus;
    private String embeddingModel;
    private Integer embeddingDimension;
    private Long embeddingDurationMs;
    private BigDecimal embeddingCost;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public byte[] getRawContent() {
        return Objects.isNull(rawContent) ? null : rawContent.clone();
    }

    public void setRawContent(byte[] rawContent) {
        this.rawContent = Objects.isNull(rawContent) ? null : rawContent.clone();
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public Long getEmbeddingDurationMs() {
        return embeddingDurationMs;
    }

    public void setEmbeddingDurationMs(Long embeddingDurationMs) {
        this.embeddingDurationMs = embeddingDurationMs;
    }

    public BigDecimal getEmbeddingCost() {
        return embeddingCost;
    }

    public void setEmbeddingCost(BigDecimal embeddingCost) {
        this.embeddingCost = embeddingCost;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
