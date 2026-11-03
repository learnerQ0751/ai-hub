package com.qtua.aihub.service.gateway;

import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.integration.ratelimit.RedisRateLimiter;
import com.qtua.aihub.service.gateway.model.GuardRequest;
import com.qtua.aihub.service.gateway.model.GuardReservation;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * Redis 网关治理实现：RPM → TPM → 预算。
 *
 * <p>预占失败时回滚已完成的预占，保证不泄漏额度；master 调用（无 keyHash）直接放行。</p>
 */
@Component
public class DefaultGatewayGuard implements GatewayGuard {

    private final RedisRateLimiter rateLimiter;

    /**
     * 构造治理实现。
     *
     * @param rateLimiter Redis 限流与预算计数器
     */
    public DefaultGatewayGuard(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * 请求前预占。
     *
     * @param request 治理请求
     * @return 预占结果
     */
    @Override
    public GuardReservation reserve(GuardRequest request) {
        if (!StringUtils.hasText(request.keyHash())) {
            return GuardReservation.none(request);
        }
        boolean tpmReserved = false;
        boolean budgetReserved = false;
        try {
            if (!rateLimiter.tryAcquireRpm(request.keyHash(), request.modelAlias(),
                    request.requestId(), request.rpmLimit())) {
                throw ChatCompletionException.rateLimitExceeded("每分钟请求数超限");
            }
            if (!ObjectUtils.isEmpty(request.tpmLimit()) && request.tpmLimit() > 0) {
                if (!rateLimiter.tryAcquireTpm(request.keyHash(), request.modelAlias(),
                        request.requestId(), request.estimatedTokens(), request.tpmLimit())) {
                    throw ChatCompletionException.rateLimitExceeded("每分钟 Token 数超限");
                }
                tpmReserved = true;
            }
            if (!ObjectUtils.isEmpty(request.maxBudget())
                    && StringUtils.hasText(request.budgetDuration())) {
                if (!rateLimiter.tryReserveBudget(request.keyHash(), request.budgetDuration(),
                        request.maxBudget(), request.estimatedCost())) {
                    throw ChatCompletionException.budgetExceeded();
                }
                budgetReserved = true;
            }
            return new GuardReservation(request.requestId(), request.keyHash(),
                    request.modelAlias(), request.budgetDuration(), request.estimatedTokens(),
                    request.estimatedCost(), tpmReserved, budgetReserved);
        } catch (RuntimeException ex) {
            rollback(request, tpmReserved, budgetReserved);
            throw ex;
        }
    }

    /**
     * 成功路径结算。
     *
     * @param reservation 预占结果
     * @param usage       实际用量
     * @param actualCost  实际成本
     */
    @Override
    public void settle(GuardReservation reservation, TokenUsage usage, BigDecimal actualCost) {
        if (reservation.tpmReserved()) {
            rateLimiter.settleTpm(reservation.keyHash(), reservation.modelAlias(),
                    reservation.requestId(), reservation.tpmEstimated(), usage.totalTokens());
        }
        if (reservation.budgetReserved()) {
            rateLimiter.settleBudget(reservation.keyHash(), reservation.budgetDuration(),
                    reservation.budgetEstimated(), actualCost);
        }
    }

    /**
     * 失败路径释放。
     *
     * @param reservation 预占结果
     */
    @Override
    public void release(GuardReservation reservation) {
        if (reservation.tpmReserved()) {
            rateLimiter.settleTpm(reservation.keyHash(), reservation.modelAlias(),
                    reservation.requestId(), reservation.tpmEstimated(), 0);
        }
        if (reservation.budgetReserved()) {
            rateLimiter.releaseBudget(reservation.keyHash(), reservation.budgetDuration(),
                    reservation.budgetEstimated());
        }
    }

    private void rollback(GuardRequest request, boolean tpmReserved, boolean budgetReserved) {
        if (tpmReserved) {
            rateLimiter.settleTpm(request.keyHash(), request.modelAlias(), request.requestId(),
                    request.estimatedTokens(), 0);
        }
        if (budgetReserved) {
            rateLimiter.releaseBudget(request.keyHash(), request.budgetDuration(),
                    request.estimatedCost());
        }
    }
}
