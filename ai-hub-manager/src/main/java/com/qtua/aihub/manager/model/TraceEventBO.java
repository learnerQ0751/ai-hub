package com.qtua.aihub.manager.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Trace 事件业务对象（REQ-4.2）。
 *
 * @param traceId   追踪 ID
 * @param tenantId  租户
 * @param projectId 项目
 * @param keyId     Key 哈希
 * @param segment   分段
 * @param name      名称
 * @param status    状态
 * @param latencyMs 耗时
 * @param payload   摘要
 * @param createdAt 时间
 */
public record TraceEventBO(String traceId, String tenantId, String projectId, String keyId,
                           String segment, String name, int status, long latencyMs,
                           Map<String, Object> payload, Instant createdAt) {

    /**
     * 防御性复制摘要。
     */
    public TraceEventBO {
        payload = Objects.isNull(payload) ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
    }
}
