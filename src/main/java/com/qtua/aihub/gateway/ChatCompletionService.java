package com.qtua.aihub.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.config.GatewayProperties;
import com.qtua.aihub.config.GatewayProperties.Deployment;
import com.qtua.aihub.gateway.router.DeploymentSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ChatCompletionService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatCompletionService.class);
    private static final String MODEL_FIELD = "model";
    private static final String MESSAGES_FIELD = "messages";
    private static final String STREAM_FIELD = "stream";

    private final GatewayProperties properties;
    private final UpstreamClient upstreamClient;
    private final ObjectMapper objectMapper;
    private final DeploymentSelector deploymentSelector;

    public ChatCompletionService(GatewayProperties properties, UpstreamClient upstreamClient,
                                 ObjectMapper objectMapper, DeploymentSelector deploymentSelector) {
        this.properties = properties;
        this.upstreamClient = upstreamClient;
        this.objectMapper = objectMapper;
        this.deploymentSelector = deploymentSelector;
    }

    public String chat(String requestBody) {
        JsonNode root = parse(requestBody);
        String alias = requireModel(root);
        rejectStreaming(root);
        requireMessages(root);
        List<Deployment> deployments = properties.getModelList().get(alias);
        if (ObjectUtils.isEmpty(deployments)) {
            throw ChatCompletionException.invalidModel(alias);
        }
        Deployment deployment = deploymentSelector.select(alias, deployments);
        LOG.info("routed alias={} upstreamModel={} apiBase={}",
                alias, deployment.getModel(), deployment.getApiBase());
        String upstreamResponse = upstreamClient.chat(deployment, rewriteModel(root, deployment.getModel()));
        return rewriteResponseModel(upstreamResponse, alias);
    }

    private String rewriteResponseModel(String upstreamResponse, String alias) {
        try {
            JsonNode responseNode = objectMapper.readTree(upstreamResponse);
            if (ObjectUtils.isEmpty(responseNode) || !responseNode.isObject()) {
                throw ChatCompletionException.upstreamError("Upstream returned an invalid JSON response", null);
            }
            ObjectNode objectNode = (ObjectNode) responseNode;
            objectNode.put(MODEL_FIELD, alias);
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.upstreamError("Upstream returned an invalid JSON response", ex);
        }
    }

    private String rewriteModel(JsonNode root, String upstreamModel) {
        ObjectNode objectNode = (ObjectNode) root;
        objectNode.put(MODEL_FIELD, upstreamModel);
        try {
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.internalError("Failed to serialize upstream request", ex);
        }
    }

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

    private String requireModel(JsonNode root) {
        JsonNode modelNode = root.get(MODEL_FIELD);
        if (ObjectUtils.isEmpty(modelNode) || modelNode.isNull() || !StringUtils.hasText(modelNode.asText())) {
            throw ChatCompletionException.invalidRequest("Field 'model' is required", MODEL_FIELD);
        }
        return modelNode.asText();
    }

    private void rejectStreaming(JsonNode root) {
        JsonNode streamNode = root.get(STREAM_FIELD);
        if (!ObjectUtils.isEmpty(streamNode) && streamNode.asBoolean(false)) {
            throw ChatCompletionException.invalidRequest("Streaming is not supported yet", STREAM_FIELD);
        }
    }

    private void requireMessages(JsonNode root) {
        JsonNode messages = root.get(MESSAGES_FIELD);
        if (ObjectUtils.isEmpty(messages) || !messages.isArray() || messages.isEmpty()) {
            throw ChatCompletionException.invalidRequest(
                    "Field 'messages' must be a non-empty array", MESSAGES_FIELD);
        }
    }
}
