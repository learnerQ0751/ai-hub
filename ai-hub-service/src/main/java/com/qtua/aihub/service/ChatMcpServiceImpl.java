package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.ChatMcpService;
import com.qtua.aihub.api.McpCallService;
import com.qtua.aihub.api.McpToolService;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.McpToolResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chat + MCP 工具编排实现（REQ-2.8）。
 *
 * <p>流程：展开 mcp 工具声明为 OpenAI function tools → 调模型 →
 * 若有 tool_calls 则经 MCP 调用代理执行并回填 messages → 再次调模型，
 * 最多 {@value #MAX_TOOL_ROUNDS} 轮。usage 按轮累计，响应只返回最终报文。</p>
 */
@Service
public class ChatMcpServiceImpl implements ChatMcpService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatMcpServiceImpl.class);
    private static final int MAX_TOOL_ROUNDS = 3;
    private static final String TYPE_MCP = "mcp";

    private final ChatCompletionService chatCompletionService;
    private final McpToolService mcpToolService;
    private final McpCallService mcpCallService;
    private final ObjectMapper objectMapper;

    /**
     * 构造服务。
     *
     * @param chatCompletionService 聊天补全服务
     * @param mcpToolService        工具目录
     * @param mcpCallService        工具调用代理
     * @param objectMapper          JSON 解析/构造
     */
    public ChatMcpServiceImpl(ChatCompletionService chatCompletionService,
                              McpToolService mcpToolService,
                              McpCallService mcpCallService,
                              ObjectMapper objectMapper) {
        this.chatCompletionService = chatCompletionService;
        this.mcpToolService = mcpToolService;
        this.mcpCallService = mcpCallService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行带 MCP 工具的聊天补全。
     *
     * @param reqDTO 原始请求（含 mcp tools 声明）
     * @return 最终响应（usage 各轮累计）
     */
    @Override
    public ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO) {
        JsonNode root = parse(reqDTO.getRawBody());
        if (!root.isObject()) {
            throw ChatCompletionException.invalidRequest("请求体必须是 JSON 对象", "body");
        }
        Set<String> serverNames = collectMcpServers(root);
        if (serverNames.isEmpty()) {
            return chatCompletionService.chat(reqDTO);
        }
        ObjectNode working = root.deepCopy();
        expandTools(working, serverNames);
        TokenUsage totalUsage = TokenUsage.ZERO;
        ChatCompletionResDTO last = null;
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            last = chatCompletionService.chat(new ChatCompletionReqDTO(
                    working.toString(), reqDTO.getAuth(), reqDTO.getRequestId()));
            totalUsage = add(totalUsage, last.getUsage());
            JsonNode toolCalls = extractToolCalls(last.getRawResponse());
            if (toolCalls.isEmpty()) {
                return withUsage(last, totalUsage);
            }
            appendAssistantAndToolResults(working, last.getRawResponse(), toolCalls);
        }
        LOG.warn("chat with mcp reached max tool rounds: requestId={}", reqDTO.getRequestId());
        return withUsage(last, totalUsage);
    }

    private Set<String> collectMcpServers(JsonNode root) {
        Set<String> serverNames = new LinkedHashSet<>();
        JsonNode tools = root.path("tools");
        if (!tools.isArray()) {
            return serverNames;
        }
        for (JsonNode tool : tools) {
            if (!TYPE_MCP.equals(tool.path("type").asText())) {
                continue;
            }
            String serverName = tool.path("server_name").asText("");
            if (!StringUtils.hasText(serverName)) {
                throw ChatCompletionException.invalidRequest(
                        "MCP 工具声明缺少 server_name", "tools");
            }
            serverNames.add(serverName);
        }
        return serverNames;
    }

    /**
     * 展开 mcp 声明为 function tools，普通 function 工具原样保留。
     */
    private void expandTools(ObjectNode working, Set<String> serverNames) {
        ArrayNode expanded = objectMapper.createArrayNode();
        JsonNode originalTools = working.path("tools");
        if (originalTools.isArray()) {
            for (JsonNode tool : originalTools) {
                if (!TYPE_MCP.equals(tool.path("type").asText())) {
                    expanded.add(tool);
                }
            }
        }
        for (String serverName : serverNames) {
            List<McpToolResDTO> tools = mcpToolService.list(serverName, null).tools();
            if (tools.isEmpty()) {
                throw ChatCompletionException.invalidRequest(
                        "MCP Server '" + serverName + "' 未注册或没有可用工具", "tools");
            }
            for (McpToolResDTO tool : tools) {
                ObjectNode function = expanded.addObject().put("type", "function")
                        .putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                try {
                    function.set("parameters", objectMapper.readTree(tool.inputSchema()));
                } catch (Exception ex) {
                    function.set("parameters", objectMapper.createObjectNode().put("type", "object"));
                }
            }
        }
        working.set("tools", expanded);
    }

    private JsonNode extractToolCalls(String rawResponse) {
        JsonNode message = parse(rawResponse).path("choices").path(0).path("message");
        JsonNode toolCalls = message.path("tool_calls");
        return toolCalls.isArray() ? toolCalls : objectMapper.createArrayNode();
    }

    private void appendAssistantAndToolResults(ObjectNode working, String rawResponse,
                                               JsonNode toolCalls) {
        JsonNode message = parse(rawResponse).path("choices").path(0).path("message");
        JsonNode messagesNode = working.path("messages");
        ArrayNode messages = messagesNode.isArray()
                ? (ArrayNode) messagesNode : working.putArray("messages");
        if (message.isObject()) {
            messages.add(message.deepCopy());
        }
        for (JsonNode toolCall : toolCalls) {
            String toolName = toolCall.path("function").path("name").asText("");
            String arguments = toolCall.path("function").path("arguments").asText("{}");
            String resultText;
            String toolCallId = toolCall.path("id").asText("");
            try {
                String body = "{\"arguments\":" + normalizeArguments(arguments) + "}";
                resultText = extractText(mcpCallService.call(toolName, body));
            } catch (RuntimeException ex) {
                resultText = "工具执行失败：" + ex.getMessage();
            }
            ObjectNode toolMessage = objectMapper.createObjectNode();
            toolMessage.put("role", "tool");
            toolMessage.put("tool_call_id", toolCallId);
            toolMessage.put("content", resultText);
            messages.add(toolMessage);
        }
    }

    private String normalizeArguments(String arguments) {
        try {
            JsonNode node = objectMapper.readTree(arguments);
            return node.isObject() ? node.toString() : "{}";
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String extractText(String resultJson) {
        try {
            JsonNode content = objectMapper.readTree(resultJson).path("content");
            if (content.isArray()) {
                StringBuilder text = new StringBuilder();
                for (JsonNode item : content) {
                    if ("text".equals(item.path("type").asText())) {
                        if (!text.isEmpty()) {
                            text.append('\n');
                        }
                        text.append(item.path("text").asText(""));
                    }
                }
                return text.toString();
            }
            return resultJson;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return resultJson;
        }
    }

    private ChatCompletionResDTO withUsage(ChatCompletionResDTO source, TokenUsage usage) {
        if (Objects.isNull(source)) {
            return new ChatCompletionResDTO("{}", usage, null);
        }
        return new ChatCompletionResDTO(source.getRawResponse(), usage,
                source.getDeploymentModel(), source.getServedAlias(),
                source.getRetryCount(), source.isFallbackUsed());
    }

    private TokenUsage add(TokenUsage left, TokenUsage right) {
        if (Objects.isNull(right)) {
            return left;
        }
        return new TokenUsage(left.promptTokens() + right.promptTokens(),
                left.completionTokens() + right.completionTokens(),
                left.totalTokens() + right.totalTokens());
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw ChatCompletionException.invalidRequest("请求体不是合法的 JSON", "body");
        }
    }
}
