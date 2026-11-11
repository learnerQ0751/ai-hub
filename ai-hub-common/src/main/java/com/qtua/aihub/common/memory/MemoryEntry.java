package com.qtua.aihub.common.memory;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一条召回的记忆。
 *
 * @param memoryId   记忆 ID
 * @param tenantId   租户 ID
 * @param projectId  项目 ID
 * @param userId     用户 ID
 * @param sessionId  会话 ID；用户级记忆为 null
 * @param memoryType 类型：SESSION / USER
 * @param content    记忆内容
 * @param metadata   结构化元信息
 * @param createdAt  创建时间
 * @param expiresAt  过期时间；null 表示永不过期
 */
public record MemoryEntry(String memoryId, String tenantId, String projectId, String userId,
                          String sessionId, String memoryType, String content,
                          Map<String, Object> metadata, Instant createdAt, Instant expiresAt) {

    /**
     * 防御性复制 metadata，避免内部可变状态外泄。
     */
    public MemoryEntry {
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
