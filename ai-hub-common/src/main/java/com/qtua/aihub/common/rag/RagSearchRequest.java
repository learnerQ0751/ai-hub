package com.qtua.aihub.common.rag;

import java.util.Objects;

/**
 * 一次检索请求的领域参数。
 *
 * <p>由 Service 完成参数校验与范围收敛后构造，Core 只做检索编排；
 * `candidateLimit` 是放大后的向量召回条数，必须大于等于 `topK`。</p>
 *
 * @param projectId      项目 ID
 * @param query          查询文本
 * @param topK           最终返回条数
 * @param minScore       最小相似度（含）
 * @param filter         元数据过滤
 * @param rerank         是否请求规则重排（服务端总开关在 Service 收敛）
 * @param candidateLimit 向量召回条数（放大候选）
 */
public record RagSearchRequest(String projectId, String query, int topK, double minScore,
                               RagSearchFilter filter, boolean rerank, int candidateLimit) {

    /**
     * 校验必填字段与基本范围。
     */
    public RagSearchRequest {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(query, "query");
        filter = Objects.isNull(filter) ? RagSearchFilter.empty() : filter;
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (candidateLimit < 1) {
            throw new IllegalArgumentException("candidateLimit must be positive");
        }
        if (!Double.isFinite(minScore)) {
            throw new IllegalArgumentException("minScore must be finite");
        }
    }
}
