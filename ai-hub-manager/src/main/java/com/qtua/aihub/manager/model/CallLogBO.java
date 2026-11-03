package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.metering.TokenUsage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 调用日志业务对象。
 *
 * <p>core 在成功或失败路径构造并交给 {@code CallLogManager} 异步落库；
 * 金额使用 {@link BigDecimal}，Token 用量来自上游 usage。
 * 容错轨迹（servedAlias / retryCount / fallbackUsed）由 REQ-1.6 引入。</p>
 *
 * @param requestId       请求追踪 ID
 * @param keyId           虚拟 Key 的 SHA-256；master 调用为 null
 * @param modelAlias      请求的模型别名
 * @param deploymentModel 上游真实模型名；未路由到部署时为 null
 * @param usage           Token 用量
 * @param cost            本次成本
 * @param latencyMs       端到端耗时（毫秒）
 * @param status          返回给客户端的 HTTP 状态码
 * @param errorType       错误/限流类型；成功为 null
 * @param tenantId        租户 ID；本期为 null
 * @param createdAt       发生时间
 * @param servedAlias     最终服务的别名；正常等于请求别名
 * @param retryCount      本请求内失败尝试次数
 * @param fallbackUsed    是否发生别名级 fallback
 * @param callType        调用类型：CHAT/MCP/PROMPT（REQ-2.3）
 * @param targetName      调用目标：MCP 工具名 / Prompt 名（REQ-2.3）
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
        String errorType,
        String tenantId,
        Instant createdAt,
        String servedAlias,
        int retryCount,
        boolean fallbackUsed,
        String callType,
        String targetName) {

    /**
     * 兼容构造器：聊天链路（callType=CHAT）。
     */
    public CallLogBO(String requestId, String keyId, String modelAlias, String deploymentModel,
                     TokenUsage usage, BigDecimal cost, long latencyMs, int status,
                     String errorType, String tenantId, Instant createdAt, String servedAlias,
                     int retryCount, boolean fallbackUsed) {
        this(requestId, keyId, modelAlias, deploymentModel, usage, cost, latencyMs, status,
                errorType, tenantId, createdAt, servedAlias, retryCount, fallbackUsed, "CHAT", null);
    }

    /**
     * 兼容构造器：无容错轨迹时使用。
     *
     * @param requestId       请求追踪 ID
     * @param keyId           虚拟 Key 哈希
     * @param modelAlias      请求别名
     * @param deploymentModel 上游模型
     * @param usage           Token 用量
     * @param cost            成本
     * @param latencyMs       耗时
     * @param status          HTTP 状态码
     * @param errorType       错误类型
     * @param tenantId        租户 ID
     * @param createdAt       发生时间
     */
    public CallLogBO(String requestId, String keyId, String modelAlias, String deploymentModel,
                     TokenUsage usage, BigDecimal cost, long latencyMs, int status,
                     String errorType, String tenantId, Instant createdAt) {
        this(requestId, keyId, modelAlias, deploymentModel, usage, cost, latencyMs, status,
                errorType, tenantId, createdAt, modelAlias, 0, false, "CHAT", null);
    }

    /**
     * 归一化可空字段，避免下游 NPE。
     */
    public CallLogBO {
        Objects.requireNonNull(usage, "usage must not be null");
        cost = Objects.isNull(cost) ? BigDecimal.ZERO : cost;
        createdAt = Objects.isNull(createdAt) ? Instant.now() : createdAt;
        callType = Objects.isNull(callType) ? "CHAT" : callType;
    }
}
