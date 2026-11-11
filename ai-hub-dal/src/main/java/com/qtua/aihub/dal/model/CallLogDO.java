package com.qtua.aihub.dal.model;

import com.qtua.aihub.common.constant.CallTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * `call_logs` 表数据对象。
 *
 * <p>只做字段承载；计量计算与异步写入策略在 manager/core 层。</p>
 */
public class CallLogDO {

    private Long id;
    /** 入口生成的请求追踪 ID。 */
    private String requestId;
    /** 虚拟 Key 的 SHA-256；master 调用为 null。 */
    private String keyId;
    /** 请求使用的模型别名。 */
    private String modelAlias;
    /** 路由到的上游真实模型名；调用失败在选择前发生时可能为 null。 */
    private String deploymentModel;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    /** 按价格表计算的成本；默认 0，避免未计价调用写入 NULL。 */
    private BigDecimal cost = BigDecimal.ZERO;
    private long latencyMs;
    /** 返回给客户端的 HTTP 状态码。 */
    private int status;
    /** 错误/限流类型，如 rate_limit_error、budget_exceeded。 */
    private String errorType;
    /** 租户 ID；本期未启用。 */
    private String tenantId;
    private Instant createdAt;
    /** 本请求内失败尝试次数（REQ-1.6）。 */
    private int retryCount;
    /** 是否发生别名级 fallback（REQ-1.6）。 */
    private boolean fallbackUsed;
    /** 最终服务的别名；正常等于请求别名，fallback 时为备用别名。 */
    private String servedAlias;
    /** 调用类型：CHAT/MCP/PROMPT/MEMORY（REQ-2.3、REQ-3.5）。 */
    private String callType = CallTypes.CHAT;
    /** 调用目标：MCP 工具名 / Prompt 名等（REQ-2.3）。 */
    private String targetName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getModelAlias() {
        return modelAlias;
    }

    public void setModelAlias(String modelAlias) {
        this.modelAlias = modelAlias;
    }

    public String getDeploymentModel() {
        return deploymentModel;
    }

    public void setDeploymentModel(String deploymentModel) {
        this.deploymentModel = deploymentModel;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getServedAlias() {
        return servedAlias;
    }

    public void setServedAlias(String servedAlias) {
        this.servedAlias = servedAlias;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }
}
