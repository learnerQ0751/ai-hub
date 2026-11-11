package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `memories` 表数据对象。
 *
 * <p>结构化作用域 + 文本内容 + JSONB metadata；过期时间由查询过滤，不依赖定时清理。</p>
 */
public class MemoryDO {

    private Long id;
    /** 对外记忆 ID（UUID）。 */
    private String memoryId;
    private String tenantId;
    private String projectId;
    private String userId;
    /** 会话 ID；用户级记忆为 null。 */
    private String sessionId;
    /** 类型：SESSION / USER。 */
    private String memoryType;
    private String content;
    /** JSONB 文本；查询时 CAST 为 VARCHAR。 */
    private String metadata;
    /** 过期时间；null 表示永不过期。 */
    private Instant expiresAt;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
