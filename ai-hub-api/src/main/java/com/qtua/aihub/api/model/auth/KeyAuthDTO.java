package com.qtua.aihub.api.model.auth;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 已认证调用方上下文。
 *
 * <p>由 AuthFilter 写入请求属性，Controller 读取后传给 Service；
 * 不包含明文 Key。限流与预算字段来自 api_keys，master 调用为空。</p>
 *
 * @param keyHash        虚拟 Key 的 SHA-256 摘要；master key 调用时为 null
 * @param models         虚拟 Key 的模型白名单；空表示全部
 * @param masterKey      是否为 master key 调用
 * @param rpmLimit       每分钟请求上限；null 表示不限
 * @param tpmLimit       每分钟 Token 上限；null 表示不限
 * @param maxBudget      预算上限；null 表示不限
 * @param budgetDuration 预算周期：daily/monthly；null 表示不启用周期预算
 */
public record KeyAuthDTO(String keyHash, List<String> models, boolean masterKey,
                         Integer rpmLimit, Integer tpmLimit,
                         BigDecimal maxBudget, String budgetDuration) {

    /**
     * 防御性复制白名单。
     */
    public KeyAuthDTO {
        models = Objects.isNull(models) ? List.of() : List.copyOf(models);
    }

    /**
     * master key 调用方（全权限）。
     *
     * @return 认证上下文
     */
    public static KeyAuthDTO master() {
        return new KeyAuthDTO(null, List.of(), true, null, null, null, null);
    }

    /**
     * 虚拟 Key 调用方（仅基础信息，不限流/不预算；兼容旧调用）。
     *
     * @param keyHash SHA-256 摘要
     * @param models  模型白名单
     * @return 认证上下文
     */
    public static KeyAuthDTO virtualKey(String keyHash, List<String> models) {
        return virtualKey(keyHash, models, null, null, null, null);
    }

    /**
     * 虚拟 Key 调用方。
     *
     * @param keyHash        SHA-256 摘要
     * @param models         模型白名单
     * @param rpmLimit       每分钟请求上限
     * @param tpmLimit       每分钟 Token 上限
     * @param maxBudget      预算上限
     * @param budgetDuration 预算周期
     * @return 认证上下文
     */
    public static KeyAuthDTO virtualKey(String keyHash, List<String> models,
                                        Integer rpmLimit, Integer tpmLimit,
                                        BigDecimal maxBudget, String budgetDuration) {
        return new KeyAuthDTO(keyHash, models, false, rpmLimit, tpmLimit, maxBudget, budgetDuration);
    }
}
