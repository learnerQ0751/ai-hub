package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 虚拟 Key 信息响应，结构对齐 LiteLLM `/key/info`。
 *
 * @param key  掩码后的 Key（不返回完整明文）
 * @param info Key 元信息
 */
public record KeyInfoResDTO(String key, KeyInfo info) {

    /**
     * Key 元信息。
     *
     * @param spend      已消费金额
     * @param totalSpend 总消费金额（本期与 spend 相同）
     * @param models     模型白名单；空表示不限制
     * @param expires    过期时间（ISO-8601）；null 表示不过期
     * @param maxBudget      预算上限
     * @param budgetDuration 预算周期：daily/monthly
     * @param rpmLimit       每分钟请求上限
     * @param tpmLimit       每分钟 Token 上限
     * @param blocked        是否封禁
     * @param createdAt      创建时间（ISO-8601）
     * @param metadata       元信息
     * @param lastActive     最近活跃时间（本期不采集，固定 null）
     */
    public record KeyInfo(
            BigDecimal spend,
            @JsonProperty("total_spend") BigDecimal totalSpend,
            List<String> models,
            String expires,
            @JsonProperty("max_budget") BigDecimal maxBudget,
            @JsonProperty("budget_duration") String budgetDuration,
            @JsonProperty("rpm_limit") Integer rpmLimit,
            @JsonProperty("tpm_limit") Integer tpmLimit,
            boolean blocked,
            @JsonProperty("created_at") String createdAt,
            Map<String, Object> metadata,
            @JsonProperty("last_active") String lastActive) {

        /**
         * 防御性复制集合，避免调用方修改内部状态。
         */
        public KeyInfo {
            models = Objects.isNull(models) ? List.of() : List.copyOf(models);
            metadata = Objects.isNull(metadata) || metadata.isEmpty()
                    ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }
}
