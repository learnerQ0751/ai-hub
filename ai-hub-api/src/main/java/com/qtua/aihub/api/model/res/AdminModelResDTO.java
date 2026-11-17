package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 管理端模型别名视图（REQ-4.4）。
 *
 * @param alias                 别名
 * @param tenantId              所属租户
 * @param description           描述
 * @param status                状态
 * @param fallbacks             fallback 链
 * @param inputPricePerMillion  输入单价（每 1M tokens，按主部署上游模型）
 * @param outputPricePerMillion 输出单价（每 1M tokens）
 * @param createdAt             创建时间
 * @param updatedAt             更新时间
 * @param deployments           部署实例
 */
public record AdminModelResDTO(String alias, @JsonProperty("tenant_id") String tenantId,
                               String description, String status, List<String> fallbacks,
                               @JsonProperty("input_price_per_million")
                               BigDecimal inputPricePerMillion,
                               @JsonProperty("output_price_per_million")
                               BigDecimal outputPricePerMillion,
                               @JsonProperty("created_at") String createdAt,
                               @JsonProperty("updated_at") String updatedAt,
                               List<AdminDeploymentResDTO> deployments) {

    /**
     * 防御性复制。
     */
    public AdminModelResDTO {
        fallbacks = Objects.isNull(fallbacks) ? List.of() : List.copyOf(fallbacks);
        deployments = Objects.isNull(deployments) ? List.of() : List.copyOf(deployments);
    }
}
