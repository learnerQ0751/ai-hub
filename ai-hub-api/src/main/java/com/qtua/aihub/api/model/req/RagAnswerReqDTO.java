package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 生成请求（HTTP 契约）。
 *
 * @param query           问题（必填）
 * @param projectId       项目 ID（必填）
 * @param model           模型别名（必填；内部 chat 使用）
 * @param topK            检索条数（可选，默认取 answer.default-top-k）
 * @param minScore        最小相似度（可选）
 * @param filters         元数据过滤（可选）
 * @param rerank          是否重排（可选；服务端总开关优先）
 * @param allowFreeAnswer 无召回时是否允许自由回答（可选；服务端开关优先）
 */
public record RagAnswerReqDTO(
        @JsonProperty("query") String query,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("model") String model,
        @JsonProperty("top_k") Integer topK,
        @JsonProperty("min_score") Double minScore,
        @JsonProperty("filters") RagSearchFilterDTO filters,
        @JsonProperty("rerank") Boolean rerank,
        @JsonProperty("allow_free_answer") Boolean allowFreeAnswer) {
}
