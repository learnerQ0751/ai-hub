package com.qtua.aihub.manager.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 管理端别名视图（含部署状态，不含凭证）。
 *
 * @param alias       别名
 * @param tenantId    租户
 * @param description 描述
 * @param fallbacks   降级链
 * @param status      状态
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 * @param deployments 部署实例
 */
public record ModelAliasAdminBO(String alias, String tenantId, String description,
                                List<String> fallbacks, String status,
                                Instant createdAt, Instant updatedAt,
                                List<ModelDeploymentAdminBO> deployments) {

    /**
     * 防御性复制。
     */
    public ModelAliasAdminBO {
        fallbacks = Objects.isNull(fallbacks) ? List.of() : List.copyOf(fallbacks);
        deployments = Objects.isNull(deployments) ? List.of() : List.copyOf(deployments);
    }
}
