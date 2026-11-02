package com.qtua.aihub.integration.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.common.constant.JsonFields;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * OpenAI 协议适配器。
 *
 * <p>负责请求/响应中的 model 字段改写与 JSON 序列化；core 层不直接操作报文。</p>
 */
@Component
public class OpenAiProtocolAdapter {

    private final ObjectMapper objectMapper;

    /**
     * 构造协议适配器。
     *
     * @param objectMapper Jackson 2 序列化器
     */
    public OpenAiProtocolAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把请求中的用户别名改写为上游真实模型名。
     *
     * @param requestBody   原始请求 JSON
     * @param upstreamModel 上游真实模型名
     * @return 改写后的请求 JSON
     */
    public String rewriteRequestModel(String requestBody, String upstreamModel) {
        try {
            JsonNode requestNode = objectMapper.readTree(requestBody);
            if (ObjectUtils.isEmpty(requestNode) || !requestNode.isObject()) {
                throw ChatCompletionException.invalidRequest("Request body must be a JSON object", null);
            }
            ObjectNode objectNode = (ObjectNode) requestNode;
            objectNode.put(JsonFields.MODEL, upstreamModel);
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.invalidRequest("Invalid JSON request body", null);
        }
    }

    /**
     * 从上游响应中解析 Token 用量。
     *
     * <p>计量失败不能影响已成功的调用：缺失 usage、非法 JSON 或负数字段
     * 统一按 0 处理；上游缺失 total 时用输入+输出兜底。</p>
     *
     * @param responseBody 上游原始响应 JSON
     * @return Token 用量；无法解析时为零用量
     */
    public TokenUsage extractUsage(String responseBody) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);
            if (ObjectUtils.isEmpty(responseNode) || !responseNode.isObject()) {
                return TokenUsage.ZERO;
            }
            JsonNode usage = responseNode.path(JsonFields.USAGE);
            if (!usage.isObject()) {
                return TokenUsage.ZERO;
            }
            int promptTokens = nonNegativeInt(usage, JsonFields.PROMPT_TOKENS);
            int completionTokens = nonNegativeInt(usage, JsonFields.COMPLETION_TOKENS);
            int totalTokens = nonNegativeInt(usage, JsonFields.TOTAL_TOKENS);
            if (totalTokens == 0) {
                totalTokens = promptTokens + completionTokens;
            }
            return new TokenUsage(promptTokens, completionTokens, totalTokens);
        } catch (JsonProcessingException ex) {
            return TokenUsage.ZERO;
        }
    }

    /**
     * 读取非负整数字段；缺失或负数按 0 处理。
     *
     * @param usage 用量 JSON
     * @param field 字段名
     * @return 非负整数
     */
    private int nonNegativeInt(JsonNode usage, String field) {
        JsonNode node = usage.get(field);
        if (ObjectUtils.isEmpty(node) || !node.canConvertToInt()) {
            return 0;
        }
        return Math.max(0, node.asInt());
    }

    /**
     * 把上游响应的 model 回写为请求别名。
     *
     * @param responseBody 上游原始响应 JSON
     * @param alias        请求别名
     * @return 改写后的响应 JSON
     */
    public String rewriteResponseModel(String responseBody, String alias) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);
            if (ObjectUtils.isEmpty(responseNode) || !responseNode.isObject()) {
                throw ChatCompletionException.upstreamError("Upstream returned an invalid JSON response", null);
            }
            ObjectNode objectNode = (ObjectNode) responseNode;
            objectNode.put(JsonFields.MODEL, alias);
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.upstreamError("Upstream returned an invalid JSON response", ex);
        }
    }
}
