package com.qtua.aihub.api.model.auth;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 已认证调用方上下文。
 *
 * <p>由 AuthFilter 写入请求属性，Controller 读取后传给 Service；
 * 不包含明文 Key。限流与预算字段来自 api_keys，master 调用为空。
 * 租户与项目来自 Key 绑定（REQ-4.1），授权项目来自项目级授权表。</p>
 *
 * @param keyHash            虚拟 Key 的 SHA-256 摘要；master key 调用时为 null
 * @param models             虚拟 Key 的模型白名单；空表示全部
 * @param masterKey          是否为 master key 调用
 * @param rpmLimit           每分钟请求上限；null 表示不限
 * @param tpmLimit           每分钟 Token 上限；null 表示不限
 * @param maxBudget          预算上限；null 表示不限
 * @param budgetDuration     预算周期：daily/monthly；null 表示不启用周期预算
 * @param tenantId           Key 绑定的租户；master/未绑定为 null
 * @param projectId          Key 绑定的项目；master/未绑定为 null
 * @param authorizedProjects 显式授权的跨项目列表（同租户）
 */
public record KeyAuthDTO(String keyHash, List<String> models, boolean masterKey,
                         Integer rpmLimit, Integer tpmLimit,
                         BigDecimal maxBudget, String budgetDuration,
                         String tenantId, String projectId,
                         List<String> authorizedProjects) {

    /**
     * 防御性复制白名单与授权项目。
     */
    public KeyAuthDTO {
        models = Objects.isNull(models) ? List.of() : List.copyOf(models);
        authorizedProjects = Objects.isNull(authorizedProjects)
                ? List.of() : List.copyOf(authorizedProjects);
    }

    /**
     * 兼容构造器：未绑定租户（REQ-4.1 之前调用/测试）。
     */
    public KeyAuthDTO(String keyHash, List<String> models, boolean masterKey,
                      Integer rpmLimit, Integer tpmLimit,
                      BigDecimal maxBudget, String budgetDuration) {
        this(keyHash, models, masterKey, rpmLimit, tpmLimit, maxBudget, budgetDuration,
                null, null, List.of());
    }

    /**
     * master key 调用方（全权限）。
     *
     * @return 认证上下文
     */
    public static KeyAuthDTO master() {
        return new KeyAuthDTO(null, List.of(), true, null, null, null, null,
                null, null, List.of());
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
        return virtualKey(keyHash, models, rpmLimit, tpmLimit, maxBudget, budgetDuration,
                null, null, List.of());
    }

    /**
     * 虚拟 Key 调用方（含租户绑定，REQ-4.1）。
     *
     * @param keyHash            SHA-256 摘要
     * @param models             模型白名单
     * @param rpmLimit           每分钟请求上限
     * @param tpmLimit           每分钟 Token 上限
     * @param maxBudget          预算上限
     * @param budgetDuration     预算周期
     * @param tenantId           绑定租户
     * @param projectId          绑定项目
     * @param authorizedProjects 授权跨项目
     * @return 认证上下文
     */
    public static KeyAuthDTO virtualKey(String keyHash, List<String> models,
                                        Integer rpmLimit, Integer tpmLimit,
                                        BigDecimal maxBudget, String budgetDuration,
                                        String tenantId, String projectId,
                                        List<String> authorizedProjects) {
        return new KeyAuthDTO(keyHash, models, false, rpmLimit, tpmLimit, maxBudget,
                budgetDuration, tenantId, projectId, authorizedProjects);
    }
}
