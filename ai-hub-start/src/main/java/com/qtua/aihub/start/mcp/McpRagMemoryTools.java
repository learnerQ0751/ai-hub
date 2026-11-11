package com.qtua.aihub.start.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.api.MemoryService;
import com.qtua.aihub.api.RagAnswerService;
import com.qtua.aihub.api.RagDocumentService;
import com.qtua.aihub.api.RagSearchService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.MemoryClearReqDTO;
import com.qtua.aihub.api.model.req.MemoryRecallReqDTO;
import com.qtua.aihub.api.model.req.MemorySaveReqDTO;
import com.qtua.aihub.api.model.req.RagAnswerReqDTO;
import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.MemoryClearResDTO;
import com.qtua.aihub.api.model.res.MemoryRecallResDTO;
import com.qtua.aihub.api.model.res.MemorySaveResDTO;
import com.qtua.aihub.api.model.res.RagAnswerResDTO;
import com.qtua.aihub.api.model.res.RagDocumentResDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * RAG 与记忆的 MCP 工具实现（REQ-3.6）。
 *
 * <p>工具入参字段与 REST 契约一致（snake_case），统一转发到内部 Service，不复制业务逻辑；
 * 租户/项目/用户由工具入参显式透传，记忆权限完全复用 REQ-3.5 的作用域与跨项目开关。
 * 编排与限流/调用日志仍由 GatewayGovernanceFilter 统一完成。</p>
 */
@Component
public class McpRagMemoryTools {

    /** 工具名：文档接入。 */
    public static final String TOOL_INGEST_DOCUMENT = "ingest_document";
    /** 工具名：知识检索。 */
    public static final String TOOL_SEARCH_KNOWLEDGE = "search_knowledge";
    /** 工具名：RAG 生成。 */
    public static final String TOOL_RAG_ANSWER = "rag_answer";
    /** 工具名：保存记忆。 */
    public static final String TOOL_SAVE_MEMORY = "save_memory";
    /** 工具名：召回记忆。 */
    public static final String TOOL_RECALL_MEMORY = "recall_memory";
    /** 工具名：清理记忆。 */
    public static final String TOOL_CLEAR_MEMORY = "clear_memory";

    private static final Set<String> TOOL_NAMES = Set.of(TOOL_INGEST_DOCUMENT, TOOL_SEARCH_KNOWLEDGE,
            TOOL_RAG_ANSWER, TOOL_SAVE_MEMORY, TOOL_RECALL_MEMORY, TOOL_CLEAR_MEMORY);
    private static final String PARAM_PROJECT_ID = "project_id";
    private static final String PARAM_FILE_NAME = "file_name";
    private static final String PARAM_CONTENT_TYPE = "content_type";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_CONTENT_BASE64 = "content_base64";
    private static final String PARAM_ARGUMENTS = "arguments";
    private static final Logger LOG = LoggerFactory.getLogger(McpRagMemoryTools.class);

    private final RagDocumentService ragDocumentService;
    private final RagSearchService ragSearchService;
    private final RagAnswerService ragAnswerService;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /**
     * 构造工具组件。
     *
     * @param ragDocumentService 文档接入服务
     * @param ragSearchService   检索服务
     * @param ragAnswerService   生成服务
     * @param memoryService      记忆服务
     * @param objectMapper       Jackson 2 序列化器（构建 JSON Schema）
     */
    public McpRagMemoryTools(RagDocumentService ragDocumentService,
                             RagSearchService ragSearchService,
                             RagAnswerService ragAnswerService,
                             MemoryService memoryService,
                             ObjectMapper objectMapper) {
        this.ragDocumentService = ragDocumentService;
        this.ragSearchService = ragSearchService;
        this.ragAnswerService = ragAnswerService;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 是否为 RAG/记忆工具。
     *
     * @param toolName 工具名
     * @return true 表示由本组件处理
     */
    public boolean supports(String toolName) {
        return TOOL_NAMES.contains(toolName);
    }

    /**
     * 追加 6 个工具的 JSON Schema 定义。
     *
     * @param tools MCP tools/list 的工具数组
     */
    public void appendToolDefinitions(ArrayNode tools) {
        tools.add(tool(TOOL_INGEST_DOCUMENT, "接入知识文档（文本或 base64），返回文档视图",
                ingestSchema()));
        tools.add(tool(TOOL_SEARCH_KNOWLEDGE, "按项目检索知识切片，返回内容、来源与相似度",
                searchSchema()));
        tools.add(tool(TOOL_RAG_ANSWER, "检索知识并生成带引用的答案", answerSchema()));
        tools.add(tool(TOOL_SAVE_MEMORY, "保存会话级或用户级记忆", saveMemorySchema()));
        tools.add(tool(TOOL_RECALL_MEMORY, "按作用域召回未过期记忆", recallMemorySchema()));
        tools.add(tool(TOOL_CLEAR_MEMORY, "按用户/会话物理删除记忆并写审计", clearMemorySchema()));
    }

    /**
     * 执行工具调用。
     *
     * @param toolName  工具名
     * @param arguments 工具参数
     * @param auth      认证上下文（含 API Key 哈希）
     * @param requestId 请求追踪 ID
     * @return 与 REST 契约一致的 JSON 文本
     */
    public String call(String toolName, JsonNode arguments, KeyAuthDTO auth, String requestId) {
        JsonNode args = Objects.isNull(arguments) || !arguments.isObject()
                ? objectMapper.createObjectNode() : arguments;
        String request = ObjectUtils.isEmpty(requestId) ? "" : requestId;
        return switch (toolName) {
            case TOOL_INGEST_DOCUMENT -> ingest(args, request);
            case TOOL_SEARCH_KNOWLEDGE -> search(args, request);
            case TOOL_RAG_ANSWER -> answer(args, auth, request);
            case TOOL_SAVE_MEMORY -> saveMemory(args, request);
            case TOOL_RECALL_MEMORY -> recallMemory(args, request);
            case TOOL_CLEAR_MEMORY -> clearMemory(args, auth, request);
            default -> throw new IllegalArgumentException("未知工具：" + toolName);
        };
    }

    private String ingest(JsonNode args, String requestId) {
        String projectId = requiredText(args, PARAM_PROJECT_ID);
        String fileName = requiredText(args, PARAM_FILE_NAME);
        byte[] content = resolveContent(args);
        String contentType = optionalText(args, PARAM_CONTENT_TYPE);
        RagDocumentResDTO document = ragDocumentService.ingest(projectId, fileName, contentType,
                content);
        // 隐私：只记录文档 ID 与字节数，不打印文档内容。
        LOG.info("MCP tool {}: requestId={}, projectId={}, docId={}, bytes={}, status={}",
                TOOL_INGEST_DOCUMENT, requestId, document.projectId(), document.docId(),
                content.length, document.indexStatus());
        return serialize(document);
    }

    private String search(JsonNode args, String requestId) {
        RagSearchReqDTO request = read(args, RagSearchReqDTO.class);
        RagSearchResDTO result = ragSearchService.search(request);
        LOG.info("MCP tool {}: requestId={}, projectId={}, count={}, noHit={}",
                TOOL_SEARCH_KNOWLEDGE, requestId, result.projectId(), result.count(),
                result.noHit());
        return serialize(result);
    }

    private String answer(JsonNode args, KeyAuthDTO auth, String requestId) {
        RagAnswerReqDTO request = read(args, RagAnswerReqDTO.class);
        RagAnswerResDTO result = ragAnswerService.answer(request, auth, requestId);
        LOG.info("MCP tool {}: requestId={}, projectId={}, model={}, citations={}, noHit={}",
                TOOL_RAG_ANSWER, requestId, result.projectId(), result.model(),
                result.citations().size(), result.noHit());
        return serialize(result);
    }

    private String saveMemory(JsonNode args, String requestId) {
        MemorySaveReqDTO request = read(args, MemorySaveReqDTO.class);
        MemorySaveResDTO result = memoryService.save(request);
        // 隐私：只记录作用域与内容长度，不打印记忆正文与 metadata。
        LOG.info("MCP tool {}: requestId={}, tenant={}, project={}, user={}, memoryId={}, "
                        + "contentLength={}", TOOL_SAVE_MEMORY, requestId, result.tenantId(),
                result.projectId(), result.userId(), result.memoryId(),
                Objects.isNull(request.content()) ? 0 : request.content().length());
        return serialize(result);
    }

    private String recallMemory(JsonNode args, String requestId) {
        MemoryRecallReqDTO request = read(args, MemoryRecallReqDTO.class);
        MemoryRecallResDTO result = memoryService.recall(request);
        LOG.info("MCP tool {}: requestId={}, tenant={}, project={}, user={}, crossProject={}, "
                        + "keyword={}, count={}", TOOL_RECALL_MEMORY, requestId,
                request.tenantId(), request.projectId(), request.userId(),
                Boolean.TRUE.equals(request.crossProject()),
                StringUtils.hasText(request.keyword()), result.count());
        return serialize(result);
    }

    private String clearMemory(JsonNode args, KeyAuthDTO auth, String requestId) {
        MemoryClearReqDTO request = read(args, MemoryClearReqDTO.class);
        String operatorKey = ObjectUtils.isEmpty(auth) ? null : auth.keyHash();
        MemoryClearResDTO result = memoryService.clear(request, operatorKey);
        LOG.info("MCP tool {}: requestId={}, tenant={}, project={}, user={}, deleted={}, "
                        + "auditId={}", TOOL_CLEAR_MEMORY, requestId, request.tenantId(),
                request.projectId(), request.userId(), result.deleted(), result.auditId());
        return serialize(result);
    }

    private byte[] resolveContent(JsonNode args) {
        String base64 = optionalText(args, PARAM_CONTENT_BASE64);
        if (StringUtils.hasText(base64)) {
            try {
                return Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException ex) {
                throw ChatCompletionException.invalidRequest(
                        "content_base64 不是合法的 Base64", PARAM_CONTENT_BASE64);
            }
        }
        String content = optionalText(args, PARAM_CONTENT);
        if (!StringUtils.hasText(content)) {
            throw ChatCompletionException.invalidRequest(
                    "content 或 content_base64 至少提供一个", PARAM_CONTENT);
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private String requiredText(JsonNode args, String field) {
        String value = optionalText(args, field);
        if (!StringUtils.hasText(value)) {
            throw ChatCompletionException.invalidRequest(field + " 不能为空", field);
        }
        return value;
    }

    private String optionalText(JsonNode args, String field) {
        JsonNode node = args.get(field);
        return Objects.isNull(node) || node.isNull() ? null : node.asText();
    }

    private <T> T read(JsonNode args, Class<T> type) {
        try {
            return jsonMapper.readValue(args.toString(), type);
        } catch (JacksonException ex) {
            throw ChatCompletionException.invalidRequest("工具参数格式不正确", PARAM_ARGUMENTS);
        }
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("工具结果序列化失败", ex);
        }
    }

    private ObjectNode tool(String name, String description, ObjectNode schema) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", schema);
        return tool;
    }

    private ObjectNode ingestSchema() {
        ObjectNode schema = baseSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject(PARAM_PROJECT_ID).put("type", "string");
        properties.putObject(PARAM_FILE_NAME).put("type", "string");
        properties.putObject(PARAM_CONTENT_TYPE).put("type", "string");
        properties.putObject(PARAM_CONTENT).put("type", "string")
                .put("description", "文本内容（UTF-8）");
        properties.putObject(PARAM_CONTENT_BASE64).put("type", "string")
                .put("description", "二进制内容（如 PDF）的 Base64 编码");
        schema.putArray("required").add(PARAM_PROJECT_ID).add(PARAM_FILE_NAME);
        ArrayNode anyOf = schema.putArray("anyOf");
        anyOf.addObject().putArray("required").add(PARAM_CONTENT);
        anyOf.addObject().putArray("required").add(PARAM_CONTENT_BASE64);
        return schema;
    }

    private ObjectNode searchSchema() {
        ObjectNode schema = baseSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query").put("type", "string");
        properties.putObject(PARAM_PROJECT_ID).put("type", "string");
        properties.putObject("top_k").put("type", "integer");
        properties.putObject("min_score").put("type", "number");
        properties.set("filters", filterSchema());
        properties.putObject("rerank").put("type", "boolean");
        schema.putArray("required").add("query").add(PARAM_PROJECT_ID);
        return schema;
    }

    private ObjectNode answerSchema() {
        ObjectNode schema = searchSchema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        properties.putObject("model").put("type", "string");
        properties.putObject("allow_free_answer").put("type", "boolean");
        schema.putArray("required").add("query").add(PARAM_PROJECT_ID).add("model");
        return schema;
    }

    private ObjectNode saveMemorySchema() {
        ObjectNode schema = baseSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("tenant_id").put("type", "string");
        properties.putObject(PARAM_PROJECT_ID).put("type", "string");
        properties.putObject("user_id").put("type", "string");
        properties.putObject("session_id").put("type", "string");
        properties.putObject(PARAM_CONTENT).put("type", "string");
        properties.putObject("metadata").put("type", "object");
        properties.putObject("ttl_seconds").put("type", "integer");
        schema.putArray("required").add("tenant_id").add(PARAM_PROJECT_ID).add("user_id")
                .add(PARAM_CONTENT);
        return schema;
    }

    private ObjectNode recallMemorySchema() {
        ObjectNode schema = baseSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("tenant_id").put("type", "string");
        properties.putObject(PARAM_PROJECT_ID).put("type", "string");
        properties.putObject("user_id").put("type", "string");
        properties.putObject("session_id").put("type", "string");
        properties.putObject("keyword").put("type", "string");
        properties.putObject("from").put("type", "string").put("format", "date-time");
        properties.putObject("to").put("type", "string").put("format", "date-time");
        properties.putObject("limit").put("type", "integer");
        properties.putObject("cross_project").put("type", "boolean");
        schema.putArray("required").add("tenant_id").add(PARAM_PROJECT_ID).add("user_id");
        return schema;
    }

    private ObjectNode clearMemorySchema() {
        ObjectNode schema = baseSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("tenant_id").put("type", "string");
        properties.putObject(PARAM_PROJECT_ID).put("type", "string");
        properties.putObject("user_id").put("type", "string");
        properties.putObject("session_id").put("type", "string");
        properties.putObject("reason").put("type", "string");
        schema.putArray("required").add("tenant_id").add(PARAM_PROJECT_ID).add("user_id");
        return schema;
    }

    private ObjectNode filterSchema() {
        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("type", "object");
        ObjectNode properties = filter.putObject("properties");
        properties.putObject("doc_ids").put("type", "array")
                .putObject("items").put("type", "string");
        properties.putObject("source").put("type", "string");
        return filter;
    }

    private ObjectNode baseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        return schema;
    }
}
