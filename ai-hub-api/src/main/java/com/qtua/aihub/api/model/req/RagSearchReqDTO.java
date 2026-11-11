package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 检索请求（HTTP 契约）。
 *
 * @param query    查询文本（必填）
 * @param projectId 项目 ID（必填）
 * @param topK     返回条数（可选，默认取配置，超上限收敛）
 * @param minScore 最小相似度（可选，范围 [-1,1]）
 * @param filters  元数据过滤（可选）
 * @param rerank   是否重排（可选；服务端总开关关闭时忽略 true）
 */
public record RagSearchReqDTO(
        @JsonProperty("query") String query,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("top_k") Integer topK,
        @JsonProperty("min_score") Double minScore,
        @JsonProperty("filters") RagSearchFilterDTO filters,
        @JsonProperty("rerank") Boolean rerank) {
}
