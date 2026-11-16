package com.qtua.aihub.manager.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 虚拟 Key 业务对象。
 *
 * <p>由 manager 从 DO 转换而来，供 service 做鉴权、查询与封禁；不包含明文 Key。</p>
 */
public class ApiKeyBO {

    private final String keyHash;
    private final List<String> models;
    private final BigDecimal spend;
    private final BigDecimal maxBudget;
    private final String budgetDuration;
    private final Integer rpmLimit;
    private final Integer tpmLimit;
    private final Instant expiresAt;
    private final boolean blocked;
    private final Map<String, Object> metadata;
    private final Instant createdAt;
    private final String tenantId;
    private final String projectId;

    /**
     * 构造业务对象。
     *
     * @param keyHash   SHA-256 摘要
     * @param models    模型白名单；空表示不限制
     * @param spend     已消费金额
     * @param maxBudget      预算上限
     * @param budgetDuration 预算周期：daily/monthly
     * @param rpmLimit       每分钟请求上限
     * @param tpmLimit       每分钟 Token 上限
     * @param expiresAt      过期时间；空表示不过期
     * @param blocked        是否封禁
     * @param metadata       元信息
     * @param createdAt      创建时间
     */
    public ApiKeyBO(String keyHash, List<String> models, BigDecimal spend, BigDecimal maxBudget,
                    String budgetDuration, Integer rpmLimit, Integer tpmLimit,
                    Instant expiresAt, boolean blocked,
                    Map<String, Object> metadata, Instant createdAt) {
        this(keyHash, models, spend, maxBudget, budgetDuration, rpmLimit, tpmLimit,
                expiresAt, blocked, metadata, createdAt, null, null);
    }

    /**
     * 构造业务对象（含租户绑定，REQ-4.1）。
     *
     * @param keyHash        SHA-256 摘要
     * @param models         模型白名单
     * @param spend          已消费金额
     * @param maxBudget      预算上限
     * @param budgetDuration 预算周期
     * @param rpmLimit       每分钟请求上限
     * @param tpmLimit       每分钟 Token 上限
     * @param expiresAt      过期时间
     * @param blocked        是否封禁
     * @param metadata       元信息
     * @param createdAt      创建时间
     * @param tenantId       绑定租户
     * @param projectId      绑定项目
     */
    public ApiKeyBO(String keyHash, List<String> models, BigDecimal spend, BigDecimal maxBudget,
                    String budgetDuration, Integer rpmLimit, Integer tpmLimit,
                    Instant expiresAt, boolean blocked,
                    Map<String, Object> metadata, Instant createdAt,
                    String tenantId, String projectId) {
        this.keyHash = keyHash;
        this.models = Objects.isNull(models) ? List.of() : List.copyOf(models);
        this.spend = spend;
        this.maxBudget = maxBudget;
        this.budgetDuration = budgetDuration;
        this.rpmLimit = rpmLimit;
        this.tpmLimit = tpmLimit;
        this.expiresAt = expiresAt;
        this.blocked = blocked;
        // metadata 允许包含 null 值（任意 JSON），不能用 Map.copyOf。
        this.metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.createdAt = createdAt;
        this.tenantId = tenantId;
        this.projectId = projectId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public List<String> getModels() {
        return List.copyOf(models);
    }

    public BigDecimal getSpend() {
        return spend;
    }

    public BigDecimal getMaxBudget() {
        return maxBudget;
    }

    public String getBudgetDuration() {
        return budgetDuration;
    }

    public Integer getRpmLimit() {
        return rpmLimit;
    }

    public Integer getTpmLimit() {
        return tpmLimit;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getProjectId() {
        return projectId;
    }
}
