package com.qtua.aihub.core;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
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
        // 先做授权再判断模型是否存在：虚拟 Key 越权请求统一返回 403，避免泄露模型目录。
        assertModelAccess(bo);
        if (ObjectUtils.isEmpty(bo.getDeployments())) {
            throw ChatCompletionException.invalidModel(bo.getAlias());
        }
        ModelDeployment deployment = deploymentSelector.select(bo.getAlias(), bo.getDeployments());
        LOG.debug("routed alias={} upstreamModel={}", bo.getAlias(), deployment.upstreamModel());
        String upstreamBody = protocolAdapter.rewriteRequestModel(bo.getRequestBody(), deployment.upstreamModel());
        String upstreamResponse = upstreamClient.chat(deployment, upstreamBody);
        return new ChatCompletionResultBO(
                protocolAdapter.rewriteResponseModel(upstreamResponse, bo.getAlias()));
    }

    /**
     * 校验调用方是否有权访问请求的模型别名。
     *
     * <p>master key 全量放行；虚拟 Key 的白名单为空表示不限模型。</p>
     *
     * @param bo 聊天补全业务对象
     */
    private void assertModelAccess(ChatCompletionBO bo) {
        if (bo.isMasterKey() || bo.getAllowedModels().isEmpty()
                || bo.getAllowedModels().contains(bo.getAlias())) {
            return;
        }
        throw ChatCompletionException.keyModelAccessDenied(bo.getAlias());
    }
}
