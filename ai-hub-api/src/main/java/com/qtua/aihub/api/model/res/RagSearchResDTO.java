package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * RAG 检索响应（HTTP 契约）。
 *
 * <p>无命中返回 200 + 空列表 + `no_hit=true`，与“服务异常”区分。</p>
 *
 * @param query    原始查询
 * @param projectId 项目 ID
 * @param count    命中条数
 * @param noHit    是否无命中
 * @param reranked 是否实际执行了规则重排
 * @param results  命中列表；无数据为空列表
 */
public record RagSearchResDTO(
        @JsonProperty("query") String query,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("count") int count,
        @JsonProperty("no_hit") boolean noHit,
        @JsonProperty("reranked") boolean reranked,
        @JsonProperty("results") List<RagSearchHitResDTO> results) {

    /**
     * 防御性复制结果列表；无数据为空列表。
     */
    public RagSearchResDTO {
        results = Objects.isNull(results) ? List.of() : List.copyOf(results);
    }
}
