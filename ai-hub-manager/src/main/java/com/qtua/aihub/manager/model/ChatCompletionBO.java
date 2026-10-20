package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.config.GatewayProperties.Deployment;

import java.util.List;

/**
 * 聊天补全业务对象。
 *
 * <p>service 层解析并校验请求后构造，core 层只消费 BO，不直接处理原始 HTTP 报文。</p>
 */
public class ChatCompletionBO {

    private final String alias;
    private final List<Deployment> deployments;
    private final String requestBody;

    /**
     * 构造业务对象。
     *
     * @param alias       模型别名
     * @param deployments 可用部署
     * @param requestBody 原始请求 JSON
     */
    public ChatCompletionBO(String alias, List<Deployment> deployments, String requestBody) {
        this.alias = alias;
        this.deployments = List.copyOf(deployments);
        this.requestBody = requestBody;
    }

    public String getAlias() {
        return alias;
    }

    public List<Deployment> getDeployments() {
        return List.copyOf(deployments);
    }

    public String getRequestBody() {
        return requestBody;
    }
}
