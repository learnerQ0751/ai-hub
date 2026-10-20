package com.qtua.aihub.integration.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.common.constant.JsonFields;
import com.qtua.aihub.common.exception.ChatCompletionException;
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
