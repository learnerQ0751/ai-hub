package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 生成虚拟 Key 响应。
 *
 * <p>{@code key} 是唯一一次返回的明文，调用方需自行保存。</p>
 *
 * @param key      明文虚拟 Key
 * @param models   模型白名单
 * @param spend    起始消费额
 * @param metadata 元信息
 * @param expires  过期时间（ISO-8601）；null 表示不过期
 * @param maxBudget      预算上限
 * @param budgetDuration 预算周期：daily/monthly
 * @param rpmLimit       每分钟请求上限
 * @param tpmLimit       每分钟 Token 上限
 * @param blocked        是否封禁
 * @param tenantId       绑定租户（REQ-4.1）
 * @param projectId      绑定项目（REQ-4.1）
 * @param alias          管理台别名
 */
public record KeyGenerateResDTO(
        String key,
        List<String> models,
        BigDecimal spend,
        Map<String, Object> metadata,
        String expires,
        @JsonProperty("max_budget") BigDecimal maxBudget,
        @JsonProperty("budget_duration") String budgetDuration,
        @JsonProperty("rpm_limit") Integer rpmLimit,
        @JsonProperty("tpm_limit") Integer tpmLimit,
        boolean blocked,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("project_id") String projectId,
        String alias) {

    /**
     * 兼容构造器：未绑定租户（旧调用/测试）。
     */
    public KeyGenerateResDTO(String key, List<String> models, BigDecimal spend,
                             Map<String, Object> metadata, String expires, BigDecimal maxBudget,
                             String budgetDuration, Integer rpmLimit, Integer tpmLimit,
                             boolean blocked) {
        this(key, models, spend, metadata, expires, maxBudget, budgetDuration, rpmLimit,
                tpmLimit, blocked, null, null, null);
    }

    /**
     * 兼容构造器：已绑定租户、未指定别名。
     */
    public KeyGenerateResDTO(String key, List<String> models, BigDecimal spend,
                             Map<String, Object> metadata, String expires, BigDecimal maxBudget,
                             String budgetDuration, Integer rpmLimit, Integer tpmLimit,
                             boolean blocked, String tenantId, String projectId) {
        this(key, models, spend, metadata, expires, maxBudget, budgetDuration, rpmLimit,
                tpmLimit, blocked, tenantId, projectId, null);
    }

    /**
     * 防御性复制集合，避免调用方修改内部状态。
     */
    public KeyGenerateResDTO {
        models = Objects.isNull(models) ? List.of() : List.copyOf(models);
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
