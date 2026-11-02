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

    /**
     * 构造响应 DTO（无用量信息）。
     *
     * @param rawResponse 上游原始响应 JSON
     */
    public ChatCompletionResDTO(String rawResponse) {
        this(rawResponse, TokenUsage.ZERO, null);
    }

    /**
     * 构造响应 DTO。
     *
     * @param rawResponse     上游原始响应 JSON
     * @param usage           实际 Token 用量
     * @param deploymentModel 实际部署模型
     */
    public ChatCompletionResDTO(String rawResponse, TokenUsage usage, String deploymentModel) {
        this.rawResponse = rawResponse;
        this.usage = usage;
        this.deploymentModel = deploymentModel;
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
}
