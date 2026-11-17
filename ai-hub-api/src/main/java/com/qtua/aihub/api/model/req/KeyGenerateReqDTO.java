package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 生成虚拟 Key 请求。
 *
 * <p>字段名对齐 LiteLLM `/key/generate`；`duration` 使用 {@code <数字><s|m|h|d>} 格式。</p>
 *
 * @param models    模型白名单；为空表示不限制
 * @param duration  有效期，如 1h、7d；为空表示不过期
 * @param metadata  任意元信息
 * @param maxBudget      预算上限
 * @param budgetDuration 预算周期：daily/monthly；为空表示不启用周期预算
 * @param rpmLimit       每分钟请求上限
 * @param tpmLimit       每分钟 Token 上限
 * @param alias          管理台展示用别名（可选）
 */
public record KeyGenerateReqDTO(
        List<String> models,
        String duration,
        Map<String, Object> metadata,
        @JsonProperty("max_budget") BigDecimal maxBudget,
        @JsonProperty("budget_duration") String budgetDuration,
        @JsonProperty("rpm_limit") Integer rpmLimit,
        @JsonProperty("tpm_limit") Integer tpmLimit,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("project_id") String projectId,
        String alias) {

    /**
     * 防御性复制集合，避免调用方修改内部状态。
     */
    public KeyGenerateReqDTO {
        models = Objects.isNull(models) ? List.of() : List.copyOf(models);
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * 兼容构造器：未指定租户/项目（绑定默认作用域）。
     */
    public KeyGenerateReqDTO(List<String> models, String duration, Map<String, Object> metadata,
                             BigDecimal maxBudget, String budgetDuration, Integer rpmLimit,
                             Integer tpmLimit) {
        this(models, duration, metadata, maxBudget, budgetDuration, rpmLimit, tpmLimit,
                null, null, null);
    }

    /**
     * 兼容构造器：未指定别名。
     */
    public KeyGenerateReqDTO(List<String> models, String duration, Map<String, Object> metadata,
                             BigDecimal maxBudget, String budgetDuration, Integer rpmLimit,
                             Integer tpmLimit, String tenantId, String projectId) {
        this(models, duration, metadata, maxBudget, budgetDuration, rpmLimit, tpmLimit,
                tenantId, projectId, null);
    }
}
