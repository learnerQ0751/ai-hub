package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 模型别名新增/更新（REQ-4.4）。
 *
 * @param alias       别名（必填）
 * @param tenantId    所属租户（可空 = 全局）
 * @param description 描述
 * @param fallbacks   fallback 别名链
 * @param status      ACTIVE/DISABLED
 * @param deployments 部署实例（仅创建时可选）
 */
public record AliasUpsertReqDTO(
        String alias,
        @JsonProperty("tenant_id") String tenantId,
        String description,
        List<String> fallbacks,
        String status,
        List<DeploymentUpsertReqDTO> deployments) {

    /**
     * 防御性复制集合。
     */
    public AliasUpsertReqDTO {
        fallbacks = Objects.isNull(fallbacks) ? List.of() : List.copyOf(fallbacks);
        deployments = Objects.isNull(deployments) ? List.of() : List.copyOf(deployments);
    }
}
