package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理台项目视图（§7.1 项目列表）。
 *
 * @param tenantId  租户
 * @param projectId 项目
 * @param name      展示名；项目表未登记时回落为 project_id
 * @param status    状态：ACTIVE / DISABLED；未登记时为 ACTIVE
 * @param createdAt 创建时间（ISO-8601）
 */
public record AdminProjectResDTO(@JsonProperty("tenant_id") String tenantId,
                                 @JsonProperty("project_id") String projectId,
                                 String name, String status,
                                 @JsonProperty("created_at") String createdAt) {
}
