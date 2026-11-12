package com.qtua.aihub.service.gateway;

import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.service.gateway.model.GuardRequest;
import com.qtua.aihub.service.gateway.model.GuardReservation;

import java.math.BigDecimal;

/**
 * 网关治理端口：限流与预算的预占、结算、释放。
 *
 * <p>当前由 Redis 实现；未来拆独立网关应用时可替换实现，调用方不变。</p>
 */
public interface GatewayGuard {

    /**
     * 请求前预占 RPM/TPM/预算；超限抛出业务异常。
     *
     * @param request 治理请求
     * @return 预占结果
     */
    GuardReservation reserve(GuardRequest request);

    /**
     * 成功路径：按实际用量结算 TPM 与预算。
     *
     * @param reservation 预占结果
     * @param usage       实际 Token 用量
     * @param actualCost  实际成本
     */
    void settle(GuardReservation reservation, TokenUsage usage, BigDecimal actualCost);

    /**
     * 失败路径：释放 TPM 与预算预占。
     *
     * @param reservation 预占结果
     */
    void release(GuardReservation reservation);

    /**
     * 事后预算校验与扣减（用于无法预估 token 的复合接口，如 RAG answer）。
     *
     * <p>响应已成功，不能回滚；本方法检查上限并原子累计实际成本，返回是否仍在预算内，
     * 供治理层记录可观测错误。</p>
     *
     * @param keyHash        虚拟 Key 哈希；master 为 null
     * @param budgetDuration 预算周期
     * @param maxBudget      预算上限
     * @param actualCost     实际成本
     * @return true 表示未超出预算（或未配置预算）
     */
    boolean chargeAfterwards(String keyHash, String budgetDuration, BigDecimal maxBudget,
                             BigDecimal actualCost);
}
