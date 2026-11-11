package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 单条记忆视图。
 *
 * @param memoryId   记忆 ID
 * @param tenantId   租户 ID
 * @param projectId  项目 ID
 * @param userId     用户 ID
 * @param sessionId  会话 ID；用户级为 null
 * @param memoryType 类型：SESSION / USER
 * @param content    记忆内容
 * @param metadata   结构化元信息
 * @param createdAt  创建时间
 * @param expiresAt  过期时间；null 表示永不过期
 */
public record MemoryResDTO(
        @JsonProperty("memory_id") String memoryId,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("memory_type") String memoryType,
        String content,
        Map<String, Object> metadata,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("expires_at") Instant expiresAt) {

    /**
     * 防御性复制 metadata。
     */
    public MemoryResDTO {
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
