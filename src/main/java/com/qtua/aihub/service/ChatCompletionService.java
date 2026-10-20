package com.qtua.aihub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.ChatCompletionException;
import com.qtua.aihub.config.GatewayProperties;
import com.qtua.aihub.config.GatewayProperties.Deployment;
import com.qtua.aihub.core.ChatCompletionCore;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 聊天补全应用服务。
 *
 * <p>负责请求解析、参数校验与别名查找；真正的路由/上游调用/改写由 core 层完成。
 * 本类不保存请求级状态，可被多实例安全使用。</p>
 */
@Service
public class ChatCompletionService {

    private static final String MODEL_FIELD = "model";
    private static final String MESSAGES_FIELD = "messages";
    private static final String STREAM_FIELD = "stream";

    private final GatewayProperties properties;
    private final ChatCompletionCore chatCompletionCore;
    private final ObjectMapper objectMapper;

    /**
     * 构造应用服务。
     *
     * @param properties         网关配置
     * @param chatCompletionCore 核心编排器
     * @param objectMapper       JSON 解析器
     */
    public ChatCompletionService(GatewayProperties properties,
                                 ChatCompletionCore chatCompletionCore,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.chatCompletionCore = chatCompletionCore;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一次聊天补全请求。
     *
     * @param requestBody 原始 JSON 请求体
     * @return 上游响应（model 已回写为别名）
     */
    public String chat(String requestBody) {
        JsonNode root = parse(requestBody);
        String alias = requireModel(root);
        rejectStreaming(root);
        requireMessages(root);
        List<Deployment> deployments = properties.getModelList().get(alias);
        if (ObjectUtils.isEmpty(deployments)) {
            throw ChatCompletionException.invalidModel(alias);
        }
        return chatCompletionCore.chat(alias, deployments, root);
    }

    /**
     * 解析 JSON 并确保是对象。
     *
     * @param requestBody 原始请求体
     * @return 解析后的 JSON
     */
    private JsonNode parse(String requestBody) {
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            if (ObjectUtils.isEmpty(root) || !root.isObject()) {
                throw ChatCompletionException.invalidRequest("Request body must be a JSON object", null);
            }
            return root;
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.invalidRequest("Invalid JSON request body", null);
        }
    }

    /**
     * 校验并读取 model 字段。
     *
     * @param root 请求 JSON
     * @return 模型别名
     */
    private String requireModel(JsonNode root) {
        JsonNode modelNode = root.get(MODEL_FIELD);
        if (ObjectUtils.isEmpty(modelNode) || modelNode.isNull() || !StringUtils.hasText(modelNode.asText())) {
            throw ChatCompletionException.invalidRequest("Field 'model' is required", MODEL_FIELD);
        }
        return modelNode.asText();
    }

    /**
     * 本期明确拒绝流式请求，避免静默返回非流式结果。
     *
     * @param root 请求 JSON
     */
    private void rejectStreaming(JsonNode root) {
        JsonNode streamNode = root.get(STREAM_FIELD);
        if (!ObjectUtils.isEmpty(streamNode) && streamNode.asBoolean(false)) {
            throw ChatCompletionException.invalidRequest("Streaming is not supported yet", STREAM_FIELD);
        }
    }

    /**
     * 校验 messages 为非空数组。
     *
     * @param root 请求 JSON
     */
    private void requireMessages(JsonNode root) {
        JsonNode messages = root.get(MESSAGES_FIELD);
        if (ObjectUtils.isEmpty(messages) || !messages.isArray() || messages.isEmpty()) {
            throw ChatCompletionException.invalidRequest(
                    "Field 'messages' must be a non-empty array", MESSAGES_FIELD);
        }
    }
}
