package com.qtua.aihub.common.tenant;

import java.util.Objects;

/**
 * 解析后的生效作用域。
 *
 * @param tenantId  生效租户
 * @param projectId 生效项目
 * @param master    是否为 master 管理请求（可跨租户）
 */
public record TenantScope(String tenantId, String projectId, boolean master) {

    /**
     * 校验必填租户。
     */
    public TenantScope {
        Objects.requireNonNull(tenantId, "tenantId");
    }
}
