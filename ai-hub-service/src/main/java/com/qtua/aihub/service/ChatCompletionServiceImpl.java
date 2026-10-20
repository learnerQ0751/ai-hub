package com.qtua.aihub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.common.config.GatewayProperties.Deployment;
import com.qtua.aihub.common.constant.JsonFields;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.core.ChatCompletionCore;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 聊天补全应用服务实现。
 *
 * <p>负责请求解析、参数校验、DTO↔BO 转换与 core 编排；真正的路由/上游调用由 core 层完成。
 * 本类不保存请求级状态，可被多实例安全使用。</p>
 */
@Service
public class ChatCompletionServiceImpl implements ChatCompletionService {

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
    public ChatCompletionServiceImpl(GatewayProperties properties,
                                     ChatCompletionCore chatCompletionCore,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.chatCompletionCore = chatCompletionCore;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一次聊天补全请求。
     *
     * @param reqDTO 原始请求
     * @return 原始响应
     */
    @Override
    public ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO) {
        String requestBody = reqDTO.getRawBody();
        JsonNode root = parse(requestBody);
        String alias = requireModel(root);
        rejectStreaming(root);
        requireMessages(root);
        List<Deployment> deployments = properties.getModelList().get(alias);
        if (ObjectUtils.isEmpty(deployments)) {
            throw ChatCompletionException.invalidModel(alias);
        }
        ChatCompletionResultBO result = chatCompletionCore.chat(
                new ChatCompletionBO(alias, deployments, requestBody));
        return new ChatCompletionResDTO(result.getResponseBody());
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
        JsonNode modelNode = root.get(JsonFields.MODEL);
        if (ObjectUtils.isEmpty(modelNode) || modelNode.isNull() || !StringUtils.hasText(modelNode.asText())) {
            throw ChatCompletionException.invalidRequest("Field 'model' is required", JsonFields.MODEL);
        }
        return modelNode.asText();
    }

    /**
     * 本期明确拒绝流式请求，避免静默返回非流式结果。
     *
     * @param root 请求 JSON
     */
    private void rejectStreaming(JsonNode root) {
        JsonNode streamNode = root.get(JsonFields.STREAM);
        if (!ObjectUtils.isEmpty(streamNode) && streamNode.asBoolean(false)) {
            throw ChatCompletionException.invalidRequest("Streaming is not supported yet", JsonFields.STREAM);
        }
    }

    /**
     * 校验 messages 为非空数组。
     *
     * @param root 请求 JSON
     */
    private void requireMessages(JsonNode root) {
        JsonNode messages = root.get(JsonFields.MESSAGES);
        if (ObjectUtils.isEmpty(messages) || !messages.isArray() || messages.isEmpty()) {
            throw ChatCompletionException.invalidRequest(
                    "Field 'messages' must be a non-empty array", JsonFields.MESSAGES);
        }
    }
}
