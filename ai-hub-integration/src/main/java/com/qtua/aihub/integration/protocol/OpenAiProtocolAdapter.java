package com.qtua.aihub.integration.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.common.constant.JsonFields;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenEstimate;
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

    /** 粗略 token 估算：约 4 个字符折合 1 个 token。 */
    private static final int CHARS_PER_TOKEN = 4;
    /** OpenAI 流结束标记。 */
    private static final String DONE_MARKER = "[DONE]";

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
     * 流式请求改写：模型名改为上游真实名，并强制注入
     * {@code stream_options.include_usage=true}，保证末块携带 usage 供计量。
     *
     * @param requestBody   原始请求 JSON（stream=true）
     * @param upstreamModel 上游真实模型名
     * @return 改写后的请求 JSON
     */
    public String rewriteStreamRequest(String requestBody, String upstreamModel) {
        try {
            JsonNode requestNode = objectMapper.readTree(requestBody);
            if (ObjectUtils.isEmpty(requestNode) || !requestNode.isObject()) {
                throw ChatCompletionException.invalidRequest("Request body must be a JSON object", null);
            }
            ObjectNode objectNode = (ObjectNode) requestNode;
            objectNode.put(JsonFields.MODEL, upstreamModel);
            JsonNode existing = objectNode.get(JsonFields.STREAM_OPTIONS);
            ObjectNode streamOptions = existing instanceof ObjectNode
                    ? (ObjectNode) existing : objectMapper.createObjectNode();
            // 覆盖客户端配置：没有 usage 就无法按实际 Token 计量与预算结算。
            streamOptions.put(JsonFields.INCLUDE_USAGE, true);
            objectNode.set(JsonFields.STREAM_OPTIONS, streamOptions);
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.invalidRequest("Invalid JSON request body", null);
        }
    }

    /**
     * 流式响应改写：把 chunk 的 model 回写为请求别名；非 JSON 事件
     * （如 {@code [DONE]}）原样透传，不阻断流。
     *
     * @param data  上游 SSE data 内容
     * @param alias 请求别名
     * @return 改写后的 data
     */
    public String rewriteStreamResponseModel(String data, String alias) {
        if (ObjectUtils.isEmpty(data) || DONE_MARKER.equals(data.trim())) {
            return data;
        }
        try {
            JsonNode responseNode = objectMapper.readTree(data);
            if (ObjectUtils.isEmpty(responseNode) || !responseNode.isObject()) {
                return data;
            }
            ObjectNode objectNode = (ObjectNode) responseNode;
            if (!objectNode.has(JsonFields.MODEL)) {
                return data;
            }
            objectNode.put(JsonFields.MODEL, alias);
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            // 非 JSON 事件（心跳/注释等）原样透传。
            return data;
        }
    }

    /**
     * 估算请求的 Token 用量，用于 TPM 与预算预占。
     *
     * <p>输入按 messages 字符数 / 4 估算，输出按 {@code max_tokens} 上限；
     * 请求未携带 max_tokens 时使用默认值。</p>
     *
     * @param requestBody      请求 JSON
     * @param defaultMaxTokens 默认输出上限
     * @return Token 估算
     */
    public TokenEstimate estimateTokens(String requestBody, int defaultMaxTokens) {
        int safeDefault = Math.max(1, defaultMaxTokens);
        try {
            JsonNode requestNode = objectMapper.readTree(requestBody);
            if (ObjectUtils.isEmpty(requestNode) || !requestNode.isObject()) {
                return TokenEstimate.of(0, safeDefault);
            }
            int maxOutput = Math.max(1, requestNode.path(JsonFields.MAX_TOKENS).asInt(safeDefault));
            int input = estimateInputTokens(requestNode);
            return TokenEstimate.of(input, maxOutput);
        } catch (JsonProcessingException ex) {
            return TokenEstimate.of(0, safeDefault);
        }
    }

    /**
     * 按 messages 文本长度估算输入 Token 数（4 字符约 1 token）。
     *
     * @param requestNode 请求 JSON
     * @return 输入 Token 估算，最少 1
     */
    private int estimateInputTokens(JsonNode requestNode) {
        JsonNode messages = requestNode.path(JsonFields.MESSAGES);
        if (!messages.isArray()) {
            return 1;
        }
        int chars = 0;
        for (JsonNode message : messages) {
            JsonNode content = message.path(JsonFields.CONTENT);
            if (content.isTextual()) {
                chars += content.asText().length();
            }
        }
        return Math.max(1, (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN);
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
