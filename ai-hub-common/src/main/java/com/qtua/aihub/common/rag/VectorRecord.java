package com.qtua.aihub.common.rag;

import java.time.Instant;
import java.util.Objects;

/**
 * 一条待写入的向量记录。
 *
 * <p>携带模型版本与维度，供检索时按模型隔离；向量数组在构造与读取时做防御性复制。</p>
 *
 * @param chunkId   切片 ID
 * @param documentId 文档 ID
 * @param projectId 项目 ID
 * @param modelId   模型标识（含版本）
 * @param dimension 向量维度
 * @param vector    向量
 * @param createdAt 创建时间
 */
public record VectorRecord(String chunkId, String documentId, String projectId,
                           String modelId, int dimension, float[] vector,
                           Instant createdAt) {

    /**
     * 校验并复制向量，避免外部修改内部状态。
     */
    public VectorRecord {
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(vector, "vector");
        vector = vector.clone();
        if (dimension <= 0 || dimension != vector.length) {
            throw new IllegalArgumentException("vector dimension does not match");
        }
        createdAt = Objects.isNull(createdAt) ? Instant.now() : createdAt;
    }

    /**
     * 返回向量副本。
     *
     * @return 向量副本
     */
    @Override
    public float[] vector() {
        return vector.clone();
    }
}
