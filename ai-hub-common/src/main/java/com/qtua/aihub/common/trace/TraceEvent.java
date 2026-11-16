package com.qtua.aihub.common.trace;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一条分段 Trace 事件（REQ-4.2）。
 *
 * @param traceId   追踪 ID（与 request_id 一致）
 * @param tenantId  租户
 * @param projectId 项目（可空）
 * @param keyHash   虚拟 Key 哈希（master 为空）
 * @param segment   分段：gateway/model/rag/mcp
 * @param name      分段名称（路径、别名、工具名等）
 * @param status    HTTP/业务状态
 * @param latencyMs 分段耗时
 * @param payload   分段摘要（不含正文与密钥）
 * @param createdAt 发生时间
 */
public record TraceEvent(String traceId, String tenantId, String projectId, String keyHash,
                         String segment, String name, int status, long latencyMs,
                         Map<String, Object> payload, Instant createdAt) {

    /**
     * 防御性复制摘要，避免异步写入前被调用方修改。
     */
    public TraceEvent {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(segment, "segment");
        payload = Objects.isNull(payload) ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        createdAt = Objects.isNull(createdAt) ? Instant.now() : createdAt;
    }
}
