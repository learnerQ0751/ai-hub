package com.qtua.aihub.manager.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 记忆业务对象。
 *
 * <p>会话级记忆绑定 project + session；用户级记忆 sessionId 为空，可在同一租户内跨项目共享。</p>
 *
 * @param memoryId   对外记忆 ID
 * @param tenantId   租户 ID
 * @param projectId  项目 ID
 * @param userId     用户 ID
 * @param sessionId  会话 ID；用户级为 null
 * @param memoryType 类型：SESSION / USER
 * @param content    记忆内容
 * @param metadata   结构化元信息
 * @param expiresAt  过期时间；null 表示永不过期
 * @param createdAt  创建时间
 */
public record MemoryBO(String memoryId, String tenantId, String projectId, String userId,
                       String sessionId, String memoryType, String content,
                       Map<String, Object> metadata, Instant expiresAt, Instant createdAt) {

    /** 会话级记忆类型。 */
    public static final String TYPE_SESSION = "SESSION";
    /** 用户级记忆类型。 */
    public static final String TYPE_USER = "USER";

    /**
     * 防御性复制 metadata。
     */
    public MemoryBO {
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
