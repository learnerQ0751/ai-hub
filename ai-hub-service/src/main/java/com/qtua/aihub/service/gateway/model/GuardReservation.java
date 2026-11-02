package com.qtua.aihub.service.gateway.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 网关治理预占结果。
 *
 * @param requestId       请求追踪 ID
 * @param keyHash         虚拟 Key 的 SHA-256
 * @param modelAlias      模型别名
 * @param budgetDuration  预算周期：daily/monthly
 * @param tpmEstimated    TPM 预占 Token 数
 * @param budgetEstimated 预算预占金额
 * @param tpmReserved     是否已预占 TPM
 * @param budgetReserved  是否已预占预算
 */
public record GuardReservation(
        String requestId,
        String keyHash,
        String modelAlias,
        String budgetDuration,
        long tpmEstimated,
        BigDecimal budgetEstimated,
        boolean tpmReserved,
        boolean budgetReserved) {

    /**
     * 归一化金额，避免下游空指针。
     */
    public GuardReservation {
        budgetEstimated = Objects.isNull(budgetEstimated) ? BigDecimal.ZERO : budgetEstimated;
    }

    /**
     * 未预占任何额度的空结果（master key 或无限制）。
     *
     * @param request 治理请求
     * @return 空预占
     */
    public static GuardReservation none(GuardRequest request) {
        return new GuardReservation(request.requestId(), request.keyHash(), request.modelAlias(),
                request.budgetDuration(), request.estimatedTokens(), BigDecimal.ZERO, false, false);
    }
}
