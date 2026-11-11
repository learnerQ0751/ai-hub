package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 保存记忆请求。
 *
 * <p>JSON 字段使用 snake_case，与记忆服务 MCP 契约保持一致（REQ-3.6 直接复用）。
 * session_id 为空表示用户级记忆；ttl_seconds 为空表示永不过期。</p>
 *
 * @param tenantId   租户 ID
 * @param projectId  项目 ID
 * @param userId     用户 ID
 * @param sessionId  会话 ID（可选）；为空表示用户级记忆
 * @param content    记忆内容
 * @param metadata   结构化元信息（可选）
 * @param ttlSeconds 存活秒数（可选）
 */
public record MemorySaveReqDTO(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("session_id") String sessionId,
        String content,
        Map<String, Object> metadata,
        @JsonProperty("ttl_seconds") Long ttlSeconds) {

    /**
     * 防御性复制 metadata，避免调用方修改内部状态。
     */
    public MemorySaveReqDTO {
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
