package com.qtua.aihub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
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
 * 模型目录通过 {@link ModelCatalog} 端口获取，不直接依赖配置类。本类不保存请求级状态，可被多实例安全使用。</p>
 */
@Service
public class ChatCompletionServiceImpl implements ChatCompletionService {

    private final ModelCatalog modelCatalog;
    private final ChatCompletionCore chatCompletionCore;
    private final ObjectMapper objectMapper;

    /**
     * 构造应用服务。
     *
     * @param modelCatalog       模型目录
     * @param chatCompletionCore 核心编排器
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
     * @return 原始响应
     */
    @Override
    public ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO) {
        //请求数据解析
        String requestBody = reqDTO.getRawBody();
        JsonNode root = parse(requestBody);
        //从请求信息中拿取请求的模型别名
        String alias = requireModel(root);
        //临时校验：拒绝流式输出
        rejectStreaming(root);
        //校验空prompt请求
        requireMessages(root);
        //获取该别名下的模型实例
        List<ModelDeployment> deployments = modelCatalog.findDeployments(alias);
        // 授权与未知模型判断都下沉 core：虚拟 Key 越权优先返回 403。
        ChatCompletionBO bo = new ChatCompletionBO(alias, deployments, requestBody,
                reqDTO.getAuth().models(), reqDTO.getAuth().masterKey(),
                reqDTO.getRequestId(), reqDTO.getAuth().keyHash(),
                reqDTO.getAuth().rpmLimit(), reqDTO.getAuth().tpmLimit(),
                reqDTO.getAuth().maxBudget(), reqDTO.getAuth().budgetDuration());
        ChatCompletionResultBO result = chatCompletionCore.chat(bo);
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
