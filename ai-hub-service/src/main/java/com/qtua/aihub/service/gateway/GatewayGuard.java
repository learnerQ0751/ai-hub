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
}
