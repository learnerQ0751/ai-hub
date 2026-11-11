package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.mapper.MemoryAuditMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.6 端到端验收：MCP Client 调用 RAG 与记忆工具、跨项目授权、未授权拒绝与调用日志。
 *
 * <p>使用 H2 + 确定性 embedding + 内存向量存储；跨项目共享开关保持默认关闭。</p>
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagMemoryMcpIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String TENANT = "t-mcp";
    private static final String USER = "u-mcp";
    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream-chat",
             "choices":[{"index":0,"message":{"role":"assistant","content":"入职满一年 5 天 [1]"},
                         "finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
            """;
    private static final MockWebServer UPSTREAM = startUpstream();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private MemoryAuditMapper memoryAuditMapper;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_rag_memory_mcp;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
    }

    @AfterAll
    static void stopServers() throws IOException {
        UPSTREAM.shutdown();
    }

    @Test
    void mcp_client_discovers_rag_and_memory_tools() throws Exception {
        try (McpClient client = newClient()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).extracting(ToolSpecification::name)
                    .contains("chat_completion", "list_models", "prompts_get",
                            "ingest_document", "search_knowledge", "rag_answer",
                            "save_memory", "recall_memory", "clear_memory");
            ToolSpecification search = tools.stream()
                    .filter(tool -> "search_knowledge".equals(tool.name())).findFirst().orElseThrow();
            assertThat(search.parameters().toString())
                    .contains("query", "project_id", "top_k", "min_score", "filters", "rerank");
            ToolSpecification save = tools.stream()
                    .filter(tool -> "save_memory".equals(tool.name())).findFirst().orElseThrow();
            assertThat(save.parameters().toString())
                    .contains("tenant_id", "project_id", "user_id", "session_id", "ttl_seconds");
        }
    }

    @Test
    void ingest_and_search_tools_return_correct_chunks_with_project_isolation() throws Exception {
        try (McpClient client = newClient()) {
            JsonNode ingested = callTool(client, "ingest_document", """
                    {"project_id":"p-mcp-a","file_name":"hr.md","content":"年假 5 天 报销流程"}
                    """);
            String docId = ingested.path("doc_id").asText();
            assertThat(docId).isNotBlank();
            assertThat(ingested.path("index_status").asText()).isEqualTo("READY");

            callTool(client, "ingest_document", """
                    {"project_id":"p-mcp-b","file_name":"other.md","content":"年假 5 天 报销流程"}
                    """);

            JsonNode hit = callTool(client, "search_knowledge",
                    "{\"query\":\"年假 5 天 报销流程\",\"project_id\":\"p-mcp-a\"}");
            assertThat(hit.path("no_hit").asBoolean()).isFalse();
            assertThat(hit.path("count").asInt()).isEqualTo(1);
            JsonNode first = hit.path("results").get(0);
            assertThat(first.path("doc_id").asText()).isEqualTo(docId);
            assertThat(first.path("source").asText()).isEqualTo("hr.md");
            assertThat(first.path("content").asText()).contains("年假 5 天");

            JsonNode otherProject = callTool(client, "search_knowledge",
                    "{\"query\":\"年假 5 天 报销流程\",\"project_id\":\"p-mcp-b\"}");
            for (JsonNode result : otherProject.path("results")) {
                assertThat(result.path("doc_id").asText()).isNotEqualTo(docId);
            }

            JsonNode noHit = callTool(client, "search_knowledge",
                    "{\"query\":\"不存在的火星基地问题\",\"project_id\":\"p-mcp-empty\"}");
            assertThat(noHit.path("no_hit").asBoolean()).isTrue();
            assertThat(noHit.path("count").asInt()).isZero();
        }
    }

    @Test
    void ingest_supports_base64_content() throws Exception {
        String base64 = java.util.Base64.getEncoder()
                .encodeToString("base64 文本内容".getBytes(StandardCharsets.UTF_8));
        try (McpClient client = newClient()) {
            JsonNode ingested = callTool(client, "ingest_document", "{\"project_id\":\"p-mcp-b64\","
                    + "\"file_name\":\"note.txt\",\"content_type\":\"text/plain\","
                    + "\"content_base64\":\"" + base64 + "\"}");
            assertThat(ingested.path("doc_id").asText()).isNotBlank();
            assertThat(ingested.path("chunk_count").asInt()).isEqualTo(1);
        }
    }

    @Test
    void rag_answer_tool_returns_answer_with_citations() throws Exception {
        try (McpClient client = newClient()) {
            JsonNode ingested = callTool(client, "ingest_document", """
                    {"project_id":"p-mcp-answer","file_name":"hr.md","content":"年假 5 天 报销流程"}
                    """);
            JsonNode answered = callTool(client, "rag_answer", """
                    {"query":"年假 5 天 报销流程","project_id":"p-mcp-answer","model":"chat"}
                    """);
            assertThat(answered.path("answer").asText()).contains("5 天");
            assertThat(answered.path("citations").size()).isEqualTo(1);
            JsonNode citation = answered.path("citations").get(0);
            assertThat(citation.path("doc_id").asText()).isEqualTo(ingested.path("doc_id").asText());
            assertThat(citation.path("source").asText()).isEqualTo("hr.md");
            assertThat(answered.path("usage").path("total_tokens").asInt()).isEqualTo(15);
        }
    }

    @Test
    void memory_tools_save_recall_and_clear() throws Exception {
        String project = "p-mcp-memory";
        try (McpClient client = newClient()) {
            JsonNode saved = callTool(client, "save_memory", "{\"tenant_id\":\"" + TENANT + "\","
                    + "\"project_id\":\"" + project + "\",\"user_id\":\"" + USER + "\","
                    + "\"content\":\"用户对芒果过敏\",\"metadata\":{\"source\":\"chat\"},"
                    + "\"ttl_seconds\":3600}");
            String memoryId = saved.path("memory_id").asText();
            assertThat(memoryId).isNotBlank();
            assertThat(saved.path("memory_type").asText()).isEqualTo("USER");

            JsonNode recalled = callTool(client, "recall_memory", "{\"tenant_id\":\"" + TENANT
                    + "\",\"project_id\":\"" + project + "\",\"user_id\":\"" + USER + "\","
                    + "\"keyword\":\"芒果\"}");
            assertThat(recalled.path("count").asInt()).isEqualTo(1);
            assertThat(recalled.path("memories").get(0).path("content").asText())
                    .isEqualTo("用户对芒果过敏");
            assertThat(recalled.path("memories").get(0).path("metadata").path("source").asText())
                    .isEqualTo("chat");

            JsonNode cleared = callTool(client, "clear_memory", "{\"tenant_id\":\"" + TENANT
                    + "\",\"project_id\":\"" + project + "\",\"user_id\":\"" + USER + "\","
                    + "\"reason\":\"用户行使删除权\"}");
            assertThat(cleared.path("deleted").asInt()).isEqualTo(1);
            String auditId = cleared.path("audit_id").asText();
            assertThat(memoryAuditMapper.selectByAuditId(auditId)).isPresent();

            JsonNode afterClear = callTool(client, "recall_memory", "{\"tenant_id\":\"" + TENANT
                    + "\",\"project_id\":\"" + project + "\",\"user_id\":\"" + USER + "\"}");
            assertThat(afterClear.path("count").asInt()).isZero();
        }
    }

    @Test
    void cross_project_and_cross_tenant_memory_are_rejected_or_empty() throws Exception {
        String projectA = "p-mcp-cross-a";
        String projectB = "p-mcp-cross-b";
        try (McpClient client = newClient()) {
            callTool(client, "save_memory", "{\"tenant_id\":\"" + TENANT + "\","
                    + "\"project_id\":\"" + projectA + "\",\"user_id\":\"" + USER + "\","
                    + "\"content\":\"用户画像：偏好中文\"}");

            JsonNode isolated = callTool(client, "recall_memory", "{\"tenant_id\":\"" + TENANT
                    + "\",\"project_id\":\"" + projectB + "\",\"user_id\":\"" + USER + "\"}");
            assertThat(isolated.path("count").asInt()).isZero();
        }

        JsonNode rejected = rpc("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"recall_memory\",\"arguments\":{\"tenant_id\":\"" + TENANT
                + "\",\"project_id\":\"" + projectB + "\",\"user_id\":\"" + USER + "\","
                + "\"cross_project\":true}}}");
        assertThat(rejected.path("error").path("message").asText()).contains("跨项目");

        try (McpClient client = newClient()) {
            callTool(client, "save_memory", "{\"tenant_id\":\"" + TENANT + "\","
                    + "\"project_id\":\"" + projectA + "\",\"user_id\":\"" + USER + "\","
                    + "\"session_id\":\"s-mcp\",\"content\":\"会话内容\"}");
        }
        JsonNode sessionRejected = rpc("{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"recall_memory\",\"arguments\":{\"tenant_id\":\"" + TENANT
                + "\",\"project_id\":\"" + projectB + "\",\"user_id\":\"" + USER + "\","
                + "\"session_id\":\"s-mcp\",\"cross_project\":true}}}");
        assertThat(sessionRejected.path("error").path("message").asText()).contains("会话");

        try (McpClient client = newClient()) {
            JsonNode otherTenant = callTool(client, "recall_memory",
                    "{\"tenant_id\":\"t-other\",\"project_id\":\"" + projectA + "\","
                            + "\"user_id\":\"" + USER + "\"}");
            assertThat(otherTenant.path("count").asInt()).isZero();
        }
    }

    @Test
    void mcp_request_without_credentials_is_rejected() {
        ResponseEntity<String> response = restClient.post()
                .uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readJson(response.getBody()).path("error").path("message").asText())
                .isNotBlank();
    }

    @Test
    void mcp_tool_call_is_logged_with_tool_target_and_request_id() throws Exception {
        try (McpClient client = newClient()) {
            callTool(client, "ingest_document", """
                    {"project_id":"p-mcp-log","file_name":"log.md","content":"日志目标验证"}
                    """);
        }
        ResponseEntity<String> response = rpcEntity("{\"jsonrpc\":\"2.0\",\"id\":21,"
                + "\"method\":\"tools/call\",\"params\":{\"name\":\"search_knowledge\","
                + "\"arguments\":{\"query\":\"日志目标验证\",\"project_id\":\"p-mcp-log\"}}}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(requestId).isNotBlank();

        CallLogDO log = awaitLog(requestId);
        assertThat(log.getCallType()).isEqualTo("MCP");
        assertThat(log.getTargetName()).isEqualTo("search_knowledge");
        assertThat(log.getModelAlias()).isEqualTo("search_knowledge");
        assertThat(log.getCost()).isEqualByComparingTo("0");
        assertThat(log.getStatus()).isEqualTo(200);
    }

    private McpClient newClient() {
        return new DefaultMcpClient.Builder()
                .key("aihub-req36-test")
                .transport(new StreamableHttpMcpTransport.Builder()
                        .url("http://localhost:" + port + "/mcp")
                        .customHeaders(Map.of("Authorization", "Bearer " + MASTER_KEY))
                        .timeout(Duration.ofSeconds(20))
                        .build())
                .build();
    }

    private JsonNode callTool(McpClient client, String name, String arguments) {
        ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                .name(name)
                .arguments(arguments)
                .build());
        assertThat(result.isError()).isFalse();
        return readJson(result.resultText());
    }

    private JsonNode rpc(String body) {
        ResponseEntity<String> response = rpcEntity(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(response.getBody());
    }

    private ResponseEntity<String> rpcEntity(String body) {
        return restClient.post()
                .uri("/mcp")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + body, ex);
        }
    }

    private CallLogDO awaitLog(String requestId) {
        for (int index = 0; index < 50; index++) {
            Optional<CallLogDO> log = callLogMapper.selectByRequestId(requestId);
            if (log.isPresent()) {
                return log.get();
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return callLogMapper.selectByRequestId(requestId).orElseThrow();
    }

    private static MockWebServer startUpstream() {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (ObjectUtils.isEmpty(request.getBody())) {
                        return json(UPSTREAM_RESPONSE);
                    }
                    return json(UPSTREAM_RESPONSE);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
