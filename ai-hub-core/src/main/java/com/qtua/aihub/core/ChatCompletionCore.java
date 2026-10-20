package com.qtua.aihub.core;

import com.qtua.aihub.common.config.GatewayProperties.Deployment;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 网关核心编排。
 *
 * <p>职责：选择部署 → 协议适配 → 调用上游。只消费 BO，不直接处理 HTTP/DTO；
 * 协议字段改写由 integration 层完成。本类不保存请求级状态，可被多实例安全使用。</p>
 */
@Component
public class ChatCompletionCore {

    private static final Logger LOG = LoggerFactory.getLogger(ChatCompletionCore.class);

    private final DeploymentSelector deploymentSelector;
    private final UpstreamClient upstreamClient;
    private final OpenAiProtocolAdapter protocolAdapter;

    /**
     * 构造核心编排器。
     *
     * @param deploymentSelector 部署选择策略
     * @param upstreamClient     上游 HTTP 客户端
     * @param protocolAdapter    OpenAI 协议适配器
     */
    public ChatCompletionCore(DeploymentSelector deploymentSelector,
                              UpstreamClient upstreamClient,
                              OpenAiProtocolAdapter protocolAdapter) {
        this.deploymentSelector = deploymentSelector;
        this.upstreamClient = upstreamClient;
        this.protocolAdapter = protocolAdapter;
    }

    /**
     * 执行一次聊天补全转发。
     *
     * @param bo 聊天补全业务对象
     * @return 上游响应业务对象（响应 model 已回写为别名）
     */
    public ChatCompletionResultBO chat(ChatCompletionBO bo) {
        Deployment deployment = deploymentSelector.select(bo.getAlias(), bo.getDeployments());
        LOG.debug("routed alias={} upstreamModel={}", bo.getAlias(), deployment.getModel());
        String upstreamBody = protocolAdapter.rewriteRequestModel(bo.getRequestBody(), deployment.getModel());
        String upstreamResponse = upstreamClient.chat(deployment, upstreamBody);
        return new ChatCompletionResultBO(
                protocolAdapter.rewriteResponseModel(upstreamResponse, bo.getAlias()));
    }
}
