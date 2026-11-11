package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 保存记忆响应。
 *
 * @param memoryId   对外记忆 ID
 * @param tenantId   租户 ID
 * @param projectId  项目 ID
 * @param userId     用户 ID
 * @param sessionId  会话 ID；用户级记忆为 null
 * @param memoryType 类型：SESSION / USER
 * @param createdAt  创建时间
 * @param expiresAt  过期时间；null 表示永不过期
 */
public record MemorySaveResDTO(
        @JsonProperty("memory_id") String memoryId,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("memory_type") String memoryType,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("expires_at") Instant expiresAt) {
}
