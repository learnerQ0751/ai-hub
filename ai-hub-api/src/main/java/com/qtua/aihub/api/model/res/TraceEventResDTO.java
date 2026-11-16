package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 一次请求的分段事件（REQ-4.2）。
 *
 * @param segment   分段：gateway/model/rag/mcp/answer
 * @param name      分段名称
 * @param status    状态
 * @param latencyMs 耗时
 * @param payload   摘要
 * @param createdAt 时间
 */
public record TraceEventResDTO(String segment, String name, int status,
                               @JsonProperty("latency_ms") long latencyMs,
                               Map<String, Object> payload,
                               @JsonProperty("created_at") String createdAt) {

    /**
     * 防御性复制摘要。
     */
    public TraceEventResDTO {
        payload = java.util.Objects.isNull(payload) ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
    }
}
