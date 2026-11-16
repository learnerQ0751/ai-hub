package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 管理端模型别名视图（REQ-4.4）。
 *
 * @param alias       别名
 * @param tenantId    所属租户
 * @param status      状态
 * @param fallbacks   fallback 链
 * @param deployments 部署实例
 */
public record AdminModelResDTO(String alias, @JsonProperty("tenant_id") String tenantId,
                               String status, List<String> fallbacks,
                               List<AdminDeploymentResDTO> deployments) {

    /**
     * 防御性复制。
     */
    public AdminModelResDTO {
        fallbacks = Objects.isNull(fallbacks) ? List.of() : List.copyOf(fallbacks);
        deployments = Objects.isNull(deployments) ? List.of() : List.copyOf(deployments);
    }
}
