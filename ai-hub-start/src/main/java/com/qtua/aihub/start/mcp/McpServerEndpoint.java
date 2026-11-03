package com.qtua.aihub.start.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.ModelService;
import com.qtua.aihub.api.PromptChatService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.req.PromptRenderReqDTO;
import com.qtua.aihub.api.model.res.PromptRenderResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.start.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ai-hub 的 MCP Server 端点（REQ-2.6）。
 *
 * <p>原生 JSON-RPC 2.0 over HTTP，暴露三个工具：
 * {@code chat_completion}（复用 chat 链路）、{@code list_models}、{@code prompts_get}
 * （复用 Prompt 渲染）。鉴权与治理由 AuthFilter / GatewayGovernanceFilter 统一完成。</p>
 */
@RestController
public class McpServerEndpoint {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SERVER_NAME = "ai-hub";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String ERROR_INTERNAL = "-32603";
    private static final String ERROR_METHOD_NOT_FOUND = "-32601";

    private final ObjectMapper objectMapper;
    private final ChatCompletionService chatCompletionService;
    private final ModelService modelService;
    private final PromptChatService promptChatService;

    /**
     * 构造端点。
     *
     * @param objectMapper          JSON 解析/序列化器
     * @param chatCompletionService 聊天补全服务
     * @param modelService          模型目录服务
     * @param promptChatService     Prompt 数据面服务
     */
    public McpServerEndpoint(ObjectMapper objectMapper,
                             ChatCompletionService chatCompletionService,
                             ModelService modelService,
                             PromptChatService promptChatService) {
        this.objectMapper = objectMapper;
        this.chatCompletionService = chatCompletionService;
        this.modelService = modelService;
        this.promptChatService = promptChatService;
    }

    /**
     * 处理 JSON-RPC 请求（单请求，不支持 batch）。
     *
     * @param body    请求体
     * @param request HTTP 请求（认证与追踪上下文）
     * @return JSON-RPC 响应；通知类请求返回 202
     */
    @PostMapping(path = "/mcp",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handle(@RequestBody(required = false) String body,
                                         HttpServletRequest request) {
        JsonNode req;
        try {
            req = objectMapper.readTree(body);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse(null, "-32700", "请求体不是合法的 JSON"));
        }
        if (Objects.isNull(req) || !req.isObject()) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse(null, "-32700", "请求体必须是 JSON-RPC 对象"));
        }
        JsonNode id = req.get("id");
        String method = req.path("method").asText("");
        if (Objects.isNull(id)) {
            // notification（如 notifications/initialized）：无响应体。
            return ResponseEntity.accepted().build();
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        try {
            JsonNode result = switch (method) {
                case "initialize" -> initializeResult();
                case "ping" -> objectMapper.createObjectNode();
                case "tools/list" -> toolsListResult();
                case "tools/call" -> toolsCallResult(req.path("params"), request);
                default -> throw new MethodNotFound("Method not found: " + method);
            };
            response.set("result", result);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(response.toString());
        } catch (MethodNotFound ignored) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse(id, ERROR_METHOD_NOT_FOUND, "方法不存在：" + method));
        } catch (ChatCompletionException ex) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse(id, ERROR_INTERNAL, ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse(id, ERROR_INTERNAL, "工具调用失败"));
        }
    }

    private JsonNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return result;
    }

    private JsonNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(tool("chat_completion", "调用大模型进行聊天补全（OpenAI 兼容响应）",
                chatCompletionSchema()));
        tools.add(tool("list_models", "列出 ai-hub 可用的模型别名", emptySchema()));
        tools.add(tool("prompts_get", "渲染 Prompt 模板为消息列表（不调用模型）", promptsGetSchema()));
        return result;
    }

    private JsonNode toolsCallResult(JsonNode params, HttpServletRequest request) {
        String toolName = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");
        if (!arguments.isObject()) {
            arguments = objectMapper.createObjectNode();
        }
        String text = switch (toolName) {
            case "chat_completion" -> chatCompletionText(arguments, request);
            case "list_models" -> modelService.listModels().toString();
            case "prompts_get" -> promptsGetText(arguments);
            default -> throw new IllegalStateException("未知工具：" + toolName);
        };
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text").put("text", text);
        result.put("isError", false);
        return result;
    }

    private String chatCompletionText(JsonNode arguments, HttpServletRequest request) {
        String model = arguments.path("model").asText("");
        JsonNode messages = arguments.get("messages");
        if (model.isBlank()) {
            throw ChatCompletionException.invalidRequest("model 不能为空", "model");
        }
        if (Objects.isNull(messages) || !messages.isArray() || messages.isEmpty()) {
            throw ChatCompletionException.invalidRequest("messages 不能为空", "messages");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        if (arguments.has("temperature")) {
            body.set("temperature", arguments.get("temperature"));
        }
        if (arguments.has("max_tokens")) {
            body.set("max_tokens", arguments.get("max_tokens"));
        }
        KeyAuthDTO auth = (KeyAuthDTO) request.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return chatCompletionService.chat(
                new ChatCompletionReqDTO(body.toString(), auth, requestId)).getRawResponse();
    }

    private String promptsGetText(JsonNode arguments) {
        String name = arguments.path("name").asText("");
        JsonNode versionNode = arguments.get("version");
        Integer version = Objects.isNull(versionNode) || !versionNode.canConvertToInt()
                ? null : versionNode.asInt();
        Map<String, String> variables = new LinkedHashMap<>();
        JsonNode variablesNode = arguments.get("variables");
        if (Objects.nonNull(variablesNode) && variablesNode.isObject()) {
            variablesNode.fields().forEachRemaining(entry ->
                    variables.put(entry.getKey(), entry.getValue().asText("")));
        }
        PromptRenderResDTO rendered = promptChatService.render(name,
                new PromptRenderReqDTO(version, variables));
        try {
            return objectMapper.writeValueAsString(rendered);
        } catch (Exception ex) {
            throw new IllegalStateException("渲染结果序列化失败", ex);
        }
    }

    private ObjectNode tool(String name, String description, ObjectNode schema) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", schema);
        return tool;
    }

    private ObjectNode chatCompletionSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("model").put("type", "string");
        properties.putObject("messages").put("type", "array");
        properties.putObject("temperature").put("type", "number");
        properties.putObject("max_tokens").put("type", "integer");
        schema.putArray("required").add("model").add("messages");
        return schema;
    }

    private ObjectNode promptsGetSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("name").put("type", "string");
        properties.putObject("version").put("type", "integer");
        properties.putObject("variables").put("type", "object");
        schema.putArray("required").add("name");
        return schema;
    }

    private ObjectNode emptySchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    private String errorResponse(JsonNode id, String code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (Objects.nonNull(id)) {
            response.set("id", id);
        } else {
            response.putNull("id");
        }
        ObjectNode error = response.putObject("error");
        error.put("code", Integer.parseInt(code));
        error.put("message", message);
        return response.toString();
    }

    /**
     * 内部信号：未知方法。
     */
    private static final class MethodNotFound extends RuntimeException {
        private MethodNotFound(String message) {
            super(message);
        }
    }
}
