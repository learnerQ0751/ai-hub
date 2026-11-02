package com.qtua.aihub.api.model.res;

import com.qtua.aihub.common.metering.TokenUsage;

/**
 * OpenAI ChatCompletion 响应契约（原始报文承载）。
 *
 * <p>usage 与 deploymentModel 供网关治理层计价/结算使用，不直接返回给客户端。</p>
 */
public class ChatCompletionResDTO {

    private final String rawResponse;
    private final TokenUsage usage;
    private final String deploymentModel;
    private final String servedAlias;
    private final int retryCount;
    private final boolean fallbackUsed;

    /**
     * 构造响应 DTO（无用量信息）。
     *
     * @param rawResponse 上游原始响应 JSON
     */
    public ChatCompletionResDTO(String rawResponse) {
        this(rawResponse, TokenUsage.ZERO, null, null, 0, false);
    }

    /**
     * 构造响应 DTO（无容错轨迹）。
     *
     * @param rawResponse     上游原始响应 JSON
     * @param usage           实际 Token 用量
     * @param deploymentModel 实际部署模型
     */
    public ChatCompletionResDTO(String rawResponse, TokenUsage usage, String deploymentModel) {
        this(rawResponse, usage, deploymentModel, null, 0, false);
    }

    /**
     * 构造响应 DTO（含容错轨迹）。
     *
     * @param rawResponse     上游原始响应 JSON
     * @param usage           实际 Token 用量
     * @param deploymentModel 实际部署模型
     * @param servedAlias     最终服务别名
     * @param retryCount      失败尝试次数
     * @param fallbackUsed    是否发生别名级 fallback
     */
    public ChatCompletionResDTO(String rawResponse, TokenUsage usage, String deploymentModel,
                                String servedAlias, int retryCount, boolean fallbackUsed) {
        this.rawResponse = rawResponse;
        this.usage = usage;
        this.deploymentModel = deploymentModel;
        this.servedAlias = servedAlias;
        this.retryCount = retryCount;
        this.fallbackUsed = fallbackUsed;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    public String getDeploymentModel() {
        return deploymentModel;
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
}
