package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `memory_audit_logs` 表数据对象：物理删除审计。
 *
 * <p>记录删除作用域、实际删除条数与被删除的 memory_id 列表（最多上限条），
 * 供合规审计与用户删除权举证。</p>
 */
public class MemoryAuditDO {

    private Long id;
    /** 对外审计 ID（UUID）。 */
    private String auditId;
    private String tenantId;
    private String projectId;
    private String userId;
    private String sessionId;
    /** 操作类型：CLEAR。 */
    private String operation;
    /** 实际物理删除条数。 */
    private int deletedCount;
    /** 被删除的 memory_id JSON 数组文本。 */
    private String memoryIds;
    /** 执行删除的虚拟 Key 哈希；master 为 null。 */
    private String operatorKey;
    private String reason;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
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

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(int deletedCount) {
        this.deletedCount = deletedCount;
    }

    public String getMemoryIds() {
        return memoryIds;
    }

    public void setMemoryIds(String memoryIds) {
        this.memoryIds = memoryIds;
    }

    public String getOperatorKey() {
        return operatorKey;
    }

    public void setOperatorKey(String operatorKey) {
        this.operatorKey = operatorKey;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
