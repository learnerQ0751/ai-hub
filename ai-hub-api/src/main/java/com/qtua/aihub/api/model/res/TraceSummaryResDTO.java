package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一条 Trace 的摘要（§7.1 Trace 列表）。
 *
 * @param traceId    追踪 ID
 * @param tenantId   租户
 * @param projectId  项目
 * @param keyId      虚拟 Key 哈希
 * @param startedAt  首个事件时间（ISO-8601）
 * @param finishedAt 末个事件时间（ISO-8601）
 * @param eventCount 分段事件数
 * @param status     分段最大状态码（0 表示未记录）
 */
public record TraceSummaryResDTO(@JsonProperty("trace_id") String traceId,
                                 @JsonProperty("tenant_id") String tenantId,
                                 @JsonProperty("project_id") String projectId,
                                 @JsonProperty("key_id") String keyId,
                                 @JsonProperty("started_at") String startedAt,
                                 @JsonProperty("finished_at") String finishedAt,
                                 @JsonProperty("event_count") int eventCount,
                                 int status) {
}
