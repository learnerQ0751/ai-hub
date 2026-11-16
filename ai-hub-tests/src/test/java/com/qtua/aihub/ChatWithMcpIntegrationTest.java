package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.qtua.aihub.api.KeyService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
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
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.8 端到端验收：chat 请求声明 MCP 工具 → 模型 tool_calls → 网关执行 MCP 工具 → 回填再调模型。
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWithMcpIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private KeyService keyService;

    private String dataKey;

    /**
     * 数据面测试统一使用虚拟 Key（master key 仅控制面可用）。
     */
    @org.junit.jupiter.api.BeforeEach
    void initDataPlaneKey() {
        dataKey = TestKeys.create(keyService, "test-tenant", "test-project");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.qtua.aihub.manager.mcp.McpServerManager mcpServerManager;

    @org.springframework.beans.factory.annotation.Autowired
    private com.qtua.aihub.manager.mcp.McpToolCatalog mcpToolCatalog;

    private static final String ADMIN_KEY = "integration-master";
    private static final AtomicInteger MCP_CALLS = new AtomicInteger();
    private static final AtomicInteger UPSTREAM_CALLS = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicReference<String> LAST_STREAM_BODY =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final MockWebServer UPSTREAM = startUpstream();
    private static final MockWebServer MCP_SRV = startMcpServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        UPSTREAM_CALLS.set(0);
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_chat_mcp;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> ADMIN_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
        registry.add("gateway.pricing.models.upstream-chat.input-per-million", () -> "1.0");
        registry.add("gateway.pricing.models.upstream-chat.output-per-million", () -> "2.0");
    }

    @AfterAll
    static void stopServer() throws IOException {
        UPSTREAM.shutdown();
        MCP_SRV.shutdown();
    }

    @Test
    void chat_auto_calls_mcp_tool_and_returns_final_answer() throws Exception {
        McpTestSupport.register(mcpServerManager, mcpToolCatalog, "chat_mcp_srv",
                MCP_SRV.url("/mcp").toString(), "test-tenant");
        MCP_CALLS.set(0);

        ResponseEntity<String> response = restClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + dataKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                        + "\"tools\":[{\"type\":\"mcp\",\"server_name\":\"chat_mcp_srv\"}]}")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("最终回答");
        assertThat(MCP_CALLS.get()).isEqualTo(1);
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(2);

        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getTotalTokens()).isEqualTo(45);
    }

    @Test
    void streaming_with_mcp_expands_tools_and_proxies_sse() throws Exception {
        McpTestSupport.register(mcpServerManager, mcpToolCatalog, "chat_mcp_stream",
                MCP_SRV.url("/mcp").toString(), "test-tenant");

        ResponseEntity<String> response = restClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + dataKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"model\":\"chat\",\"stream\":true,"
                        + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                        + "\"tools\":[{\"type\":\"mcp\",\"server_name\":\"chat_mcp_stream\"}]}")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/event-stream");
        assertThat(response.getBody()).contains("chat_mcp_stream__echo");
        // 上游收到的是展开后的 function tools，不再是 mcp 声明。
        assertThat(LAST_STREAM_BODY.get()).contains("\"tools\"")
                .doesNotContain("\"type\":\"mcp\"");
    }

    @Test
    void plain_chat_is_unaffected() {
        ResponseEntity<String> response = restClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + dataKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("最终回答");
    }

    private CallLogDO awaitLog(String requestId) {
        for (int i = 0; i < 50; i++) {
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
            String toolCallResponse = """
                    {"id":"x","object":"chat.completion","model":"upstream-chat",
                     "choices":[{"index":0,"message":{"role":"assistant","content":null,
                       "tool_calls":[{"id":"call_1","type":"function",
                         "function":{"name":"chat_mcp_srv__echo","arguments":"{\\"message\\":\\"hi\\"}"}}]},
                       "finish_reason":"tool_calls"}],
                     "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """;
            String finalResponse = """
                    {"id":"y","object":"chat.completion","model":"upstream-chat",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"最终回答：hi"},
                       "finish_reason":"stop"}],
                     "usage":{"prompt_tokens":20,"completion_tokens":10,"total_tokens":30}}
                    """;
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String body = request.getBody().readString(StandardCharsets.UTF_8);
                    if (body.contains("\"stream\":true")) {
                        LAST_STREAM_BODY.set(body);
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "text/event-stream")
                                .setBody("data: {\"id\":\"s\",\"object\":\"chat.completion.chunk\","
                                        + "\"model\":\"upstream-chat\",\"choices\":[{\"index\":0,"
                                        + "\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                                        + "\"type\":\"function\",\"function\":{\"name\":"
                                        + "\"chat_mcp_stream__echo\",\"arguments\":\"{}\"}}]},"
                                        + "\"finish_reason\":null}]}\n\n"
                                        + "data: [DONE]\n\n");
                    }
                    if (body.contains("\"tools\"")) {
                        // MCP 编排：第一轮返回 tool_calls，后续返回最终回答。
                        return json(UPSTREAM_CALLS.incrementAndGet() == 1
                                ? toolCallResponse : finalResponse);
                    }
                    return json(finalResponse);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static MockWebServer startMcpServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String body = request.getBody().readString(StandardCharsets.UTF_8);
                    if (body.contains("\"initialize\"")) {
                        return json("{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":"
                                + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                                + "\"serverInfo\":{\"name\":\"chat_mcp_srv\",\"version\":\"1.0.0\"}}}");
                    }
                    if (body.contains("\"tools/list\"")) {
                        return json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                                + "{\"name\":\"echo\",\"description\":\"echo tool\","
                                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}");
                    }
                    if (body.contains("\"tools/call\"")) {
                        MCP_CALLS.incrementAndGet();
                        return json("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":"
                                + "{\"content\":[{\"type\":\"text\",\"text\":\"echo: hi\"}],"
                                + "\"isError\":false}}");
                    }
                    return new MockResponse().setResponseCode(404);
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
