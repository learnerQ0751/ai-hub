package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.metering.TokenUsage;

/**
 * 聊天补全结果业务对象。
 *
 * <p>core 只返回业务结果（响应、用量、部署模型）；计价、spend、日志由网关层处理。</p>
 */
public class ChatCompletionResultBO {

    private final String responseBody;
    private final TokenUsage usage;
    private final String deploymentModel;

    /**
     * 构造结果对象。
     *
     * @param responseBody    上游响应 JSON（model 已回写为别名）
     * @param usage           上游返回的 Token 用量
     * @param deploymentModel 实际路由到的上游模型
     */
    public ChatCompletionResultBO(String responseBody, TokenUsage usage, String deploymentModel) {
        this.responseBody = responseBody;
        this.usage = usage;
        this.deploymentModel = deploymentModel;
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
}
