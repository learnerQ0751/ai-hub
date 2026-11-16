package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `model_aliases` 表数据对象（REQ-4.1/4.4）。
 */
public class ModelAliasDO {

    private Long id;
    private String alias;
    private String tenantId;
    private String description;
    /** fallback 别名数组 JSON 文本。 */
    private String fallbacks;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFallbacks() {
        return fallbacks;
    }

    public void setFallbacks(String fallbacks) {
        this.fallbacks = fallbacks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
