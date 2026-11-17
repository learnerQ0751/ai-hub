package com.qtua.aihub.manager.model;

import java.time.Instant;

/**
 * Trace 聚合摘要（§7.1 Trace 列表）。
 *
 * @param traceId    追踪 ID
 * @param tenantId   租户
 * @param projectId  项目
 * @param keyId      Key 哈希
 * @param startedAt  首个事件时间
 * @param finishedAt 末个事件时间
 * @param eventCount 事件数
 * @param status     最大状态码
 */
public record TraceSummaryBO(String traceId, String tenantId, String projectId, String keyId,
                             Instant startedAt, Instant finishedAt, int eventCount, int status) {
}
