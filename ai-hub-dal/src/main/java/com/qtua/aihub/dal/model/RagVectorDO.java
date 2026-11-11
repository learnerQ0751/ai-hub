package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `rag_vectors` 表数据对象。
 *
 * <p>向量以 pgvector 文本形式传入/传出：写入时 CAST 为 vector，检索时用 cosine 距离。
 * 记录模型版本与维度，供模型切换后隔离旧向量。</p>
 */
public class RagVectorDO {

    private Long id;
    private String chunkId;
    private String docId;
    private String projectId;
    private String embeddingModel;
    private int embeddingDimension;
    /** pgvector 文本，例如 "[0.1,0.2]"；由 VectorStore 实现负责编解码。 */
    private String embedding;
    private Instant createdAt;
    /** 检索结果相似度（1 - cosine distance）；仅在查询结果中赋值。 */
    private Double score;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
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

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
