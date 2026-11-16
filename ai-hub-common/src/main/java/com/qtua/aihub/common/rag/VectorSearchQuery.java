package com.qtua.aihub.common.rag;

import java.util.Objects;

/**
 * 向量检索条件。
 *
 * <p>按 project_id 与模型版本隔离；topK 由调用方收敛到合理范围。</p>
 *
 * @param projectId 项目 ID
 * @param modelId   模型标识（含版本）
 * @param vector    查询向量
 * @param topK      返回条数
 * @param minScore  最小相似度（含）
 * @param tenantId  租户 ID（REQ-4.1）
 */
public record VectorSearchQuery(String projectId, String modelId, float[] vector,
                                int topK, double minScore, String tenantId) {

    /**
     * 兼容构造器：未指定租户（旧调用/测试）。
     */
    public VectorSearchQuery(String projectId, String modelId, float[] vector,
                             int topK, double minScore) {
        this(projectId, modelId, vector, topK, minScore, "default");
    }

    /**
     * 校验并复制查询向量。
     */
    public VectorSearchQuery {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(vector, "vector");
        vector = vector.clone();
    }

    /**
     * 返回查询向量副本。
     *
     * @return 向量副本
     */
    @Override
    public float[] vector() {
        return vector.clone();
    }
}
