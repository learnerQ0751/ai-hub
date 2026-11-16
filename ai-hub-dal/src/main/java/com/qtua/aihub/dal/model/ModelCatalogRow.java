package com.qtua.aihub.dal.model;

/**
 * 模型目录联表查询行：别名 + 部署实例 + 权重/优先级。
 */
public class ModelCatalogRow {

    private String alias;
    private String tenantId;
    private String fallbacks;
    private String apiBase;
    private String apiKeyCipher;
    private String upstreamModel;
    private int weight;
    private int priority;

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

    public String getFallbacks() {
        return fallbacks;
    }

    public void setFallbacks(String fallbacks) {
        this.fallbacks = fallbacks;
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    public String getApiKeyCipher() {
        return apiKeyCipher;
    }

    public void setApiKeyCipher(String apiKeyCipher) {
        this.apiKeyCipher = apiKeyCipher;
    }

    public String getUpstreamModel() {
        return upstreamModel;
    }

    public void setUpstreamModel(String upstreamModel) {
        this.upstreamModel = upstreamModel;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
