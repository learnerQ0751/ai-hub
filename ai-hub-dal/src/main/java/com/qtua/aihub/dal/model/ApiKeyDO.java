package com.qtua.aihub.dal.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * `api_keys` 表数据对象。
 *
 * <p>`models` 与 `metadata` 以 JSON 文本承载，由 manager 层负责与业务对象互转；
 * dal 层只做字段映射，不解析 JSON。</p>
 */
public class ApiKeyDO {

    private Long id;
    /** SHA-256 十六进制摘要，唯一；不允许保存明文。 */
    private String keyHash;
    /** 管理台展示用别名；可为空。 */
    private String alias;
    /** 模型白名单 JSON 数组，如 ["chat"]。 */
    private String models;
    /** 已消费金额；本期只读，1.5 起累加。 */
    private BigDecimal spend;
    private BigDecimal maxBudget;
    /** 预算周期：daily/monthly；为空表示不启用周期预算。 */
    private String budgetDuration;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private Instant expiresAt;
    private boolean blocked;
    /** 任意元信息 JSON 对象。 */
    private String metadata;
    private Instant createdAt;
    /** 绑定租户（REQ-4.1）；未绑定为 null。 */
    private String tenantId;
    /** 绑定项目（REQ-4.1）；未绑定为 null。 */
    private String projectId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public BigDecimal getSpend() {
        return spend;
    }

    public void setSpend(BigDecimal spend) {
        this.spend = spend;
    }

    public BigDecimal getMaxBudget() {
        return maxBudget;
    }

    public void setMaxBudget(BigDecimal maxBudget) {
        this.maxBudget = maxBudget;
    }

    public String getBudgetDuration() {
        return budgetDuration;
    }

    public void setBudgetDuration(String budgetDuration) {
        this.budgetDuration = budgetDuration;
    }

    public Integer getRpmLimit() {
        return rpmLimit;
    }

    public void setRpmLimit(Integer rpmLimit) {
        this.rpmLimit = rpmLimit;
    }

    public Integer getTpmLimit() {
        return tpmLimit;
    }

    public void setTpmLimit(Integer tpmLimit) {
        this.tpmLimit = tpmLimit;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
}
