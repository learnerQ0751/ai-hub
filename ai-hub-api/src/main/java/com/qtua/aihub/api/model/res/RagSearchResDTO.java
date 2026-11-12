package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * RAG 检索响应（HTTP 契约）。
 *
 * <p>无命中返回 200 + 空列表 + `no_hit=true`，与“服务异常”区分。embedding 用量/模型
 * 仅用于 Controller 回填治理上下文，不序列化给客户端。</p>
 *
 * @param query    原始查询
 * @param projectId 项目 ID
 * @param count    命中条数
 * @param noHit    是否无命中
 * @param reranked 是否实际执行了规则重排
 * @param results  命中列表；无数据为空列表
 * @param embeddingTokens 查询向量化 token（内部）
 * @param embeddingModel  查询向量化模型（内部）
 */
public record RagSearchResDTO(
        @JsonProperty("query") String query,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("count") int count,
        @JsonProperty("no_hit") boolean noHit,
        @JsonProperty("reranked") boolean reranked,
        @JsonProperty("results") List<RagSearchHitResDTO> results,
        @JsonIgnore long embeddingTokens,
        @JsonIgnore String embeddingModel) {

    /**
     * 防御性复制结果列表；无数据为空列表。
     */
    public RagSearchResDTO {
        results = Objects.isNull(results) ? List.of() : List.copyOf(results);
    }

    /**
     * 兼容构造：无 embedding 计量信息。
     *
     * @param query     查询
     * @param projectId 项目 ID
     * @param count     条数
     * @param noHit     是否无命中
     * @param reranked  是否重排
     * @param results   结果
     */
    public RagSearchResDTO(String query, String projectId, int count, boolean noHit,
                           boolean reranked, List<RagSearchHitResDTO> results) {
        this(query, projectId, count, noHit, reranked, results, 0L, null);
    }
}
