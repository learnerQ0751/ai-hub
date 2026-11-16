package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 管理端 Key 视图（不含明文与完整哈希）。
 *
 * @param keyHash     掩码后的哈希
 * @param models      模型白名单
 * @param spend       已消费
 * @param maxBudget   预算
 * @param blocked     是否封禁
 * @param tenantId    绑定租户
 * @param projectId   绑定项目
 * @param expiresAt   过期时间
 * @param createdAt   创建时间
 */
public record AdminKeyResDTO(@JsonProperty("key_hash") String keyHash, List<String> models,
                             BigDecimal spend,
                             @JsonProperty("max_budget") BigDecimal maxBudget,
                             boolean blocked,
                             @JsonProperty("tenant_id") String tenantId,
                             @JsonProperty("project_id") String projectId,
                             @JsonProperty("expires_at") String expiresAt,
                             @JsonProperty("created_at") String createdAt) {

    /**
     * 防御性复制。
     */
    public AdminKeyResDTO {
        models = Objects.isNull(models) ? List.of() : List.copyOf(models);
    }
}
