package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * RAG 生成响应（HTTP 契约）。
 *
 * <p>`no_hit` 只表示知识库是否召回；无召回且允许自由回答时 `no_hit=true` + `free_answer=true`
 * 且 `citations` 为空。`citations.index` 从 1 开始，与答案中 `[n]` 对应。
 * deploymentModel 与 embedding 用量仅用于 Controller 回填治理上下文，不序列化给客户端。</p>
 *
 * @param query     原始问题
 * @param projectId 项目 ID
 * @param answer    模型答案或兜底话术
 * @param noHit     是否无召回
 * @param freeAnswer 是否无召回自由回答
 * @param citations 实际使用的引用；无引用为空列表
 * @param usage     Token 用量
 * @param model     请求的模型别名
 * @param deploymentModel 实际部署模型（内部）
 * @param embeddingTokens 查询向量化 token（内部）
 * @param embeddingModel  查询向量化模型（内部）
 */
public record RagAnswerResDTO(
        @JsonProperty("query") String query,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("answer") String answer,
        @JsonProperty("no_hit") boolean noHit,
        @JsonProperty("free_answer") boolean freeAnswer,
        @JsonProperty("citations") List<RagCitationResDTO> citations,
        @JsonProperty("usage") RagUsageResDTO usage,
        @JsonProperty("model") String model,
        @JsonIgnore String deploymentModel,
        @JsonIgnore long embeddingTokens,
        @JsonIgnore String embeddingModel) {

    /**
     * 防御性复制引用列表；无引用为空列表。
     */
    public RagAnswerResDTO {
        citations = Objects.isNull(citations) ? List.of() : List.copyOf(citations);
    }

    /**
     * 兼容构造：无内部计量信息。
     *
     * @param query      问题
     * @param projectId  项目 ID
     * @param answer     答案
     * @param noHit      是否无召回
     * @param freeAnswer 是否自由回答
     * @param citations  引用
     * @param usage      用量
     * @param model      模型别名
     */
    public RagAnswerResDTO(String query, String projectId, String answer, boolean noHit,
                           boolean freeAnswer, List<RagCitationResDTO> citations,
                           RagUsageResDTO usage, String model) {
        this(query, projectId, answer, noHit, freeAnswer, citations, usage, model,
                null, 0L, null);
    }
}
