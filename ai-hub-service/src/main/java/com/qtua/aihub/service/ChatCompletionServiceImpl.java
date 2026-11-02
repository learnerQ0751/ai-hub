package com.qtua.aihub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.ChatCompletionStreamResDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.constant.JsonFields;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.core.ChatCompletionCore;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import com.qtua.aihub.manager.model.ChatCompletionStreamResultBO;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 聊天补全业务服务。
 *
 * <p>只负责请求解析/校验与业务执行；限流、预算、计价、spend、访问日志
 * 由网关治理层（GatewayGovernanceFilter / GatewayGovernanceService）统一处理。</p>
 */
@Service
public class ChatCompletionServiceImpl implements ChatCompletionService {

    private final ModelCatalog modelCatalog;
    private final ChatCompletionCore chatCompletionCore;
    private final ObjectMapper objectMapper;

    /**
     * 构造业务服务。
     *
     * @param modelCatalog       模型目录
     * @param chatCompletionCore 聊天补全业务编排
     * @param objectMapper       JSON 解析器
     */
    public ChatCompletionServiceImpl(ModelCatalog modelCatalog,
                                     ChatCompletionCore chatCompletionCore,
                                     ObjectMapper objectMapper) {
        this.modelCatalog = modelCatalog;
        this.chatCompletionCore = chatCompletionCore;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一次聊天补全请求。
     *
     * @param reqDTO 原始请求
     * @return 原始响应 + 实际用量
     */
    @Override
    public ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO) {
        String requestBody = reqDTO.getRawBody();
        JsonNode root = parse(requestBody);
        String alias = requireModel(root);
        rejectStreaming(root);
        requireMessages(root);
        List<ModelDeployment> deployments = modelCatalog.findDeployments(alias);
        if (ObjectUtils.isEmpty(deployments)) {
            throw ChatCompletionException.invalidModel(alias);
        }
        ChatCompletionBO bo = new ChatCompletionBO(alias, deployments, requestBody,
                reqDTO.getRequestId(), reqDTO.getAuth().keyHash());
        ChatCompletionResultBO result = chatCompletionCore.chat(bo);
        return new ChatCompletionResDTO(result.getResponseBody(), result.getUsage(),
                result.getDeploymentModel(), result.getServedAlias(),
                result.getRetryCount(), result.isFallbackUsed());
    }

    /**
     * 处理一次流式聊天补全请求。
     *
     * @param reqDTO 原始请求（stream=true）
     * @return SSE data 流与计量上下文
     */
    @Override
    public ChatCompletionStreamResDTO chatStream(ChatCompletionReqDTO reqDTO) {
        String requestBody = reqDTO.getRawBody();
        JsonNode root = parse(requestBody);
        String alias = requireModel(root);
        requireStreaming(root);
        requireMessages(root);
        List<ModelDeployment> deployments = modelCatalog.findDeployments(alias);
        if (ObjectUtils.isEmpty(deployments)) {
            throw ChatCompletionException.invalidModel(alias);
        }
        ChatCompletionBO bo = new ChatCompletionBO(alias, deployments, requestBody,
                reqDTO.getRequestId(), reqDTO.getAuth().keyHash());
        ChatCompletionStreamResultBO result = chatCompletionCore.chatStream(bo);
        return new ChatCompletionStreamResDTO(result.getEvents(), result.getUsage(),
                result.getServedAlias(), result.getDeploymentModel());
    }

    /**
     * 校验 stream 必须为 true（流式入口）。
     *
     * @param root 请求 JSON
     */
    private void requireStreaming(JsonNode root) {
        JsonNode streamNode = root.get(JsonFields.STREAM);
        if (ObjectUtils.isEmpty(streamNode) || !streamNode.asBoolean(false)) {
            throw ChatCompletionException.invalidRequest(
                    "Field 'stream' must be true", JsonFields.STREAM);
        }
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
        if (ObjectUtils.isEmpty(modelNode) || modelNode.isNull()
                || !StringUtils.hasText(modelNode.asText())) {
            throw ChatCompletionException.invalidRequest("Field 'model' is required", JsonFields.MODEL);
        }
        return modelNode.asText();
    }

    /**
     * 本期明确拒绝流式请求。
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
