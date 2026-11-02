package com.qtua.aihub.service.gateway.model;

import java.math.BigDecimal;

/**
 * 网关治理请求：限流与预算的输入。
 *
 * @param requestId        请求追踪 ID
 * @param keyHash          虚拟 Key 的 SHA-256；master 调用为 null
 * @param modelAlias       模型别名
 * @param rpmLimit         每分钟请求上限
 * @param tpmLimit         每分钟 Token 上限
 * @param maxBudget        预算上限
 * @param budgetDuration   预算周期：daily/monthly
 * @param estimatedTokens  请求前估算 Token 数
 * @param estimatedCost    请求前估算成本
 */
public record GuardRequest(
        String requestId,
        String keyHash,
        String modelAlias,
        Integer rpmLimit,
        Integer tpmLimit,
        BigDecimal maxBudget,
        String budgetDuration,
        long estimatedTokens,
        BigDecimal estimatedCost) {
}
