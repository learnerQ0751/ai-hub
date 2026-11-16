package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `model_deployments` 表数据对象（部署实例）。
 */
public class ModelDeploymentDO {

    private Long id;
    private String deploymentKey;
    private String apiBase;
    /** 上游 Key 密文；永不通过管理接口回显。 */
    private String apiKeyCipher;
    private String upstreamModel;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeploymentKey() {
        return deploymentKey;
    }

    public void setDeploymentKey(String deploymentKey) {
        this.deploymentKey = deploymentKey;
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
