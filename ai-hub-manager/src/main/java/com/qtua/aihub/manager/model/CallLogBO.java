package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.metering.TokenUsage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 调用日志业务对象。
 *
 * <p>core 在成功或失败路径构造并交给 {@code CallLogManager} 异步落库；
 * 金额使用 {@link BigDecimal}，Token 用量来自上游 usage。</p>
 *
 * @param requestId       请求追踪 ID
 * @param keyId           虚拟 Key 的 SHA-256；master 调用为 null
 * @param modelAlias      请求的模型别名
 * @param deploymentModel 上游真实模型名；未路由到部署时为 null
 * @param usage           Token 用量
 * @param cost            本次成本
 * @param latencyMs       端到端耗时（毫秒）
 * @param status          返回给客户端的 HTTP 状态码
 * @param tenantId        租户 ID；本期为 null
 * @param createdAt       发生时间
 */
public record CallLogBO(
        String requestId,
        String keyId,
        String modelAlias,
        String deploymentModel,
        TokenUsage usage,
        BigDecimal cost,
        long latencyMs,
        int status,
        String tenantId,
        Instant createdAt) {

    /**
     * 归一化可空字段，避免下游 NPE。
     */
    public CallLogBO {
        Objects.requireNonNull(usage, "usage must not be null");
        cost = Objects.isNull(cost) ? BigDecimal.ZERO : cost;
        createdAt = Objects.isNull(createdAt) ? Instant.now() : createdAt;
    }
}
