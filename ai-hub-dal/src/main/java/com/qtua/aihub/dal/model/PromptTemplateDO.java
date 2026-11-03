package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `prompt_templates` 表数据对象。
 *
 * <p>{@code variables} 以 JSON 文本承载，由 manager 层编解码。</p>
 */
public class PromptTemplateDO {

    private Long id;
    /** 模板名；同 name 多版本。 */
    private String name;
    /** 版本号，从 1 递增。 */
    private Integer version;
    /** 模板内容，含 {{var}} 占位符。 */
    private String content;
    /** 变量声明 JSON 数组。 */
    private String variables;
    /** 状态：DRAFT/PUBLISHED/ARCHIVED。 */
    private String status;
    private String tenantId;
    private String createdBy;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getVariables() {
        return variables;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
