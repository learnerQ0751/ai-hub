package com.qtua.aihub.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.config.GatewayProperties.Deployment;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * 网关核心编排。
 *
 * <p>职责：选择部署 → 改写请求 model → 调用上游 → 改写响应 model。
 * 本类不保存请求级状态，可被多实例安全使用。</p>
 */
@Service
public class ChatCompletionCore {

    private static final Logger LOG = LoggerFactory.getLogger(ChatCompletionCore.class);
    private static final String MODEL_FIELD = "model";

    private final DeploymentSelector deploymentSelector;
    private final UpstreamClient upstreamClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造核心编排器。
     *
     * @param deploymentSelector 部署选择策略
     * @param upstreamClient     上游 HTTP 客户端
     * @param objectMapper       JSON 序列化器
     */
    public ChatCompletionCore(DeploymentSelector deploymentSelector,
                              UpstreamClient upstreamClient,
                              ObjectMapper objectMapper) {
        this.deploymentSelector = deploymentSelector;
        this.upstreamClient = upstreamClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次聊天补全转发。
     *
     * @param alias       请求模型别名
     * @param deployments 该别名的部署列表
     * @param request     已解析的请求 JSON
     * @return 上游响应（响应 model 已回写为别名）
     */
    public String chat(String alias, List<Deployment> deployments, JsonNode request) {
        Deployment deployment = deploymentSelector.select(alias, deployments);
        LOG.debug("routed alias={} upstreamModel={}", alias, deployment.getModel());
        String upstreamBody = rewriteRequestModel(request, deployment.getModel());
        String upstreamResponse = upstreamClient.chat(deployment, upstreamBody);
        return rewriteResponseModel(upstreamResponse, alias);
    }

    /**
     * 把请求中的用户别名改写为上游真实模型名。
     *
     * @param request       请求 JSON
     * @param upstreamModel 上游真实模型名
     * @return 改写后的 JSON 字符串
     */
    private String rewriteRequestModel(JsonNode request, String upstreamModel) {
        ObjectNode objectNode = (ObjectNode) request;
        objectNode.put(MODEL_FIELD, upstreamModel);
        try {
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.internalError("Failed to serialize upstream request", ex);
        }
    }

    /**
     * 把上游响应的 model 回写为请求别名，保持 OpenAI 兼容语义。
     *
     * @param upstreamResponse 上游原始响应
     * @param alias            请求别名
     * @return 改写后的响应
     */
    private String rewriteResponseModel(String upstreamResponse, String alias) {
        try {
            JsonNode responseNode = objectMapper.readTree(upstreamResponse);
            if (ObjectUtils.isEmpty(responseNode) || !responseNode.isObject()) {
                throw ChatCompletionException.upstreamError(
                        "Upstream returned an invalid JSON response", null);
            }
            ObjectNode objectNode = (ObjectNode) responseNode;
            objectNode.put(MODEL_FIELD, alias);
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.upstreamError(
                    "Upstream returned an invalid JSON response", ex);
        }
    }
}
