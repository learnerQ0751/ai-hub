package com.qtua.aihub.service.gateway.model;

import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.common.metering.TokenUsage;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 一次请求的网关治理上下文。
 *
 * <p>由 Filter 在请求前创建并放入 request attribute；业务执行成功后回填
 * usage/deploymentModel，异常处理器回填 status/errorType；Filter 在请求结束后
 * 统一结算或释放。</p>
 */
public class GatewayContext {

    /** request attribute 名称。 */
    public static final String ATTRIBUTE = "gateway.governance.context";

    private final String requestId;
    private final String keyHash;
    private final String path;
    private final KeyAuthDTO auth;
    private final long startedAt = System.currentTimeMillis();

    private volatile String modelAlias;
    private volatile long estimatedTokens;
    private volatile BigDecimal estimatedCost = BigDecimal.ZERO;
    private volatile GuardReservation reservation;
    private volatile TokenUsage usage = TokenUsage.ZERO;
    private volatile String deploymentModel;
    private volatile String servedAlias;
    private volatile int retryCount;
    private volatile boolean fallbackUsed;
    private volatile int status;
    private volatile String errorType;
    private volatile boolean failed;

    /**
     * 构造上下文。
     *
     * @param requestId 请求追踪 ID
     * @param keyHash   虚拟 Key 哈希；master 为 null
     * @param path      请求路径
     * @param auth      认证上下文
     */
    public GatewayContext(String requestId, String keyHash, String path, KeyAuthDTO auth) {
        this.requestId = requestId;
        this.keyHash = keyHash;
        this.path = path;
        this.auth = auth;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getPath() {
        return path;
    }

    public KeyAuthDTO getAuth() {
        return auth;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public String getModelAlias() {
        return modelAlias;
    }

    public void setModelAlias(String modelAlias) {
        this.modelAlias = modelAlias;
    }

    public long getEstimatedTokens() {
        return estimatedTokens;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimate(long estimatedTokens, BigDecimal estimatedCost) {
        this.estimatedTokens = estimatedTokens;
        this.estimatedCost = estimatedCost;
    }

    public GuardReservation getReservation() {
        return reservation;
    }

    public void setReservation(GuardReservation reservation) {
        this.reservation = reservation;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    public String getDeploymentModel() {
        return deploymentModel;
    }

    /**
     * 业务成功回填实际用量（无容错轨迹）。
     *
     * @param usage           实际用量
     * @param deploymentModel 实际部署模型
     */
    public void complete(TokenUsage usage, String deploymentModel) {
        complete(usage, deploymentModel, modelAlias, 0, false);
    }

    /**
     * 业务成功回填实际用量与容错轨迹。
     *
     * @param usage           实际用量
     * @param deploymentModel 实际部署模型
     * @param servedAlias     最终服务别名
     * @param retryCount      失败尝试次数
     * @param fallbackUsed    是否发生别名级 fallback
     */
    public void complete(TokenUsage usage, String deploymentModel, String servedAlias,
                         int retryCount, boolean fallbackUsed) {
        this.usage = usage;
        this.deploymentModel = deploymentModel;
        this.servedAlias = servedAlias;
        this.retryCount = retryCount;
        this.fallbackUsed = fallbackUsed;
    }

    public String getServedAlias() {
        return servedAlias;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorType() {
        return errorType;
    }

    public boolean isFailed() {
        return failed;
    }

    /**
     * 标记请求失败。
     *
     * @param status    HTTP 状态码
     * @param errorType 错误类型
     */
    public void fail(int status, String errorType) {
        this.failed = true;
        this.status = status;
        this.errorType = errorType;
    }

    /**
     * 是否已解析出模型别名（日志必填字段）。
     *
     * @return true 表示可用于记录日志
     */
    public boolean hasModelAlias() {
        return StringUtils.hasText(modelAlias);
    }
}
