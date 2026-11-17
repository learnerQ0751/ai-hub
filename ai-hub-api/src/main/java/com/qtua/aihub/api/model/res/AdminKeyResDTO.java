package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理端 Key 视图（不含明文与完整哈希）。
 *
 * @param keyHash        掩码后的哈希
 * @param alias          管理台别名
 * @param models         模型白名单
 * @param spend          已消费
 * @param maxBudget      预算上限
 * @param budgetDuration 预算周期：daily/monthly
 * @param rpmLimit       每分钟请求上限
 * @param tpmLimit       每分钟 Token 上限
 * @param metadata       元信息
 * @param blocked        是否封禁
 * @param tenantId       绑定租户
 * @param projectId      绑定项目
 * @param expiresAt      过期时间
 * @param createdAt      创建时间
 */
public record AdminKeyResDTO(@JsonProperty("key_hash") String keyHash, String alias,
                             List<String> models,
                             BigDecimal spend,
                             @JsonProperty("max_budget") BigDecimal maxBudget,
                             @JsonProperty("budget_duration") String budgetDuration,
                             @JsonProperty("rpm_limit") Integer rpmLimit,
                             @JsonProperty("tpm_limit") Integer tpmLimit,
                             Map<String, Object> metadata,
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
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
