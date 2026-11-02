package com.qtua.aihub.core;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * 聊天补全业务编排。
 *
 * <p>只负责：部署选择、协议改写、上游调用、usage 解析。
 * 限流、预算、计价、spend 与访问日志属于网关治理层（service/gateway），本类不感知。
 * 本类不保存请求级状态，可被多实例安全使用。</p>
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
     * @return 上游响应、用量与实际部署模型
     */
    public ChatCompletionResultBO chat(ChatCompletionBO bo) {
        if (ObjectUtils.isEmpty(bo.getDeployments())) {
            throw ChatCompletionException.invalidModel(bo.getAlias());
        }
        ModelDeployment deployment = deploymentSelector.select(bo.getAlias(), bo.getDeployments());
        LOG.debug("routed alias={} upstreamModel={}", bo.getAlias(), deployment.upstreamModel());
        String upstreamBody = protocolAdapter.rewriteRequestModel(
                bo.getRequestBody(), deployment.upstreamModel());
        String upstreamResponse = upstreamClient.chat(deployment, upstreamBody);
        TokenUsage usage = protocolAdapter.extractUsage(upstreamResponse);
        String responseBody = protocolAdapter.rewriteResponseModel(upstreamResponse, bo.getAlias());
        return new ChatCompletionResultBO(responseBody, usage, deployment.upstreamModel());
    }
}
