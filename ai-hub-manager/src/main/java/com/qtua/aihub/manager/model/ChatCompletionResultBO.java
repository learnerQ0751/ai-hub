package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.metering.TokenUsage;

/**
 * 聊天补全结果业务对象。
 *
 * <p>core 只返回业务结果（响应、用量、部署模型与容错轨迹）；计价、spend、
 * 日志由网关治理层处理。</p>
 */
public class ChatCompletionResultBO {

    private final String responseBody;
    private final TokenUsage usage;
    private final String deploymentModel;
    private final String servedAlias;
    private final int retryCount;
    private final boolean fallbackUsed;

    /**
     * 兼容构造器：无容错轨迹时使用。
     *
     * @param responseBody    上游响应 JSON（model 已回写为别名）
     * @param usage           上游返回的 Token 用量
     * @param deploymentModel 实际路由到的上游模型
     */
    public ChatCompletionResultBO(String responseBody, TokenUsage usage, String deploymentModel) {
        this(responseBody, usage, deploymentModel, null, 0, false);
    }

    /**
     * 完整构造结果对象。
     *
     * @param responseBody    上游响应 JSON（model 已回写为请求别名）
     * @param usage           上游返回的 Token 用量
     * @param deploymentModel 实际路由到的上游模型
     * @param servedAlias     最终服务的别名（fallback 时为备用别名）
     * @param retryCount      本请求内失败尝试次数
     * @param fallbackUsed    是否发生别名级 fallback
     */
    public ChatCompletionResultBO(String responseBody, TokenUsage usage, String deploymentModel,
                                  String servedAlias, int retryCount, boolean fallbackUsed) {
        this.responseBody = responseBody;
        this.usage = usage;
        this.deploymentModel = deploymentModel;
        this.servedAlias = servedAlias;
        this.retryCount = retryCount;
        this.fallbackUsed = fallbackUsed;
    }

    public String getResponseBody() {
        return responseBody;
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
