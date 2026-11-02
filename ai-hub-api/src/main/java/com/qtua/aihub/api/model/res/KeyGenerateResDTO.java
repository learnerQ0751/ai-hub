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
 * @param maxBudget 预算上限
 * @param rpmLimit 每分钟请求上限
 * @param tpmLimit 每分钟 Token 上限
 * @param blocked  是否封禁
 */
public record KeyGenerateResDTO(
        String key,
        List<String> models,
        BigDecimal spend,
        Map<String, Object> metadata,
        String expires,
        @JsonProperty("max_budget") BigDecimal maxBudget,
        @JsonProperty("rpm_limit") Integer rpmLimit,
        @JsonProperty("tpm_limit") Integer tpmLimit,
        boolean blocked) {

    /**
     * 防御性复制集合，避免调用方修改内部状态。
     */
    public KeyGenerateResDTO {
        models = Objects.isNull(models) ? List.of() : List.copyOf(models);
        metadata = Objects.isNull(metadata) || metadata.isEmpty()
                ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
