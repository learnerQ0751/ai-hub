package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.qtua.aihub.api.KeyService;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.3 端到端验收：MCP 调用代理（路由 / 治理 / 错误归一 / 截断 / 幂等重试）。
 *
 * <p>MockWebServer 模拟 MCP Server：initialize / tools/list / tools/call。</p>
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpProxyIntegrationTest {

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
    private static final String BIG_TEXT = "x".repeat(1024 * 1024 + 2048);
    private static final MockWebServer MCP = startServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

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
        // 独立 H2：避免与其它 MCP 测试类共享注册表。
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_proxy;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> ADMIN_KEY);
    }

    @AfterAll
    static void stopServer() throws IOException {
        MCP.shutdown();
    }

    @Test
    void proxies_tool_call_and_writes_call_log() throws Exception {
        register("proxy_srv", MCP.url("/mcp").toString());

        ResponseEntity<String> response = call("proxy_srv__echo", "{\"arguments\":{\"message\":\"hi\"}}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("echo: hi");

        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(log.getCallType()).isEqualTo("MCP");
        assertThat(log.getTargetName()).isEqualTo("proxy_srv__echo");
        assertThat(log.getCost()).isEqualByComparingTo("0");
    }

    @Test
    void unknown_tool_returns_404_and_bad_arguments_400() throws Exception {
        register("proxy_err", MCP.url("/mcp").toString());

        ResponseEntity<String> notFound = call("proxy_err__nope", "{\"arguments\":{}}");
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).contains("not_found_error");

        ResponseEntity<String> badArgs = call("proxy_err__echo", "{\"arguments\":\"not-object\"}");
        assertThat(badArgs.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badArgs.getBody()).contains("invalid_request_error");
    }

    @Test
    void slow_server_returns_504() throws Exception {
        register("proxy_slow", MCP.url("/mcp/slow").toString());

        ResponseEntity<String> response = call("proxy_slow__echo", "{\"arguments\":{\"message\":\"hi\"}}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).contains("timeout_error");
    }

    @Test
    void big_result_is_truncated_with_original_size() throws Exception {
        register("proxy_big", MCP.url("/mcp").toString());

        ResponseEntity<String> response = call("proxy_big__big", "{\"arguments\":{}}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("truncated").asBoolean()).isTrue();
        assertThat(body.path("originalSize").asLong()).isGreaterThan(1024 * 1024);
    }

    @Test
    void idempotent_tool_retries_once_but_non_idempotent_does_not() throws Exception {
        register("proxy_idem", MCP.url("/mcp").toString());

        ResponseEntity<String> idem = call("proxy_idem__idem_tool", "{\"arguments\":{}}");
        assertThat(idem.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idem.getBody()).contains("idem-ok");

        ResponseEntity<String> nonIdem = call("proxy_idem__always_fail", "{\"arguments\":{}}");
        assertThat(nonIdem.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void virtual_key_rpm_limit_applies_to_mcp_calls() throws Exception {
        register("proxy_rpm", MCP.url("/mcp").toString());
        String virtualKey = keyService.generate(new com.qtua.aihub.api.model.req.KeyGenerateReqDTO(
                java.util.List.of("chat"), "1h", null, null, null, 1, null,
                "test-tenant", "test-project")).key();

        ResponseEntity<String> first = restClient.post()
                .uri("/v1/mcp/tools/proxy_rpm__echo:call")
                .header("Authorization", "Bearer " + virtualKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"arguments\":{\"message\":\"a\"}}")
                .retrieve().toEntity(String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> second = restClient.post()
                .uri("/v1/mcp/tools/proxy_rpm__echo:call")
                .header("Authorization", "Bearer " + virtualKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"arguments\":{\"message\":\"b\"}}")
                .retrieve().toEntity(String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getBody()).contains("rate_limit_error");
    }

    private void register(String name, String endpoint) {
        McpTestSupport.register(mcpServerManager, mcpToolCatalog, name, endpoint, "test-tenant");
    }

    private ResponseEntity<String> call(String tool, String body) {
        return restClient.post()
                .uri("/v1/mcp/tools/" + tool + ":call")
                .header("Authorization", "Bearer " + dataKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
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

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String body = request.getBody().readString(StandardCharsets.UTF_8);
                    String path = request.getPath() == null ? "" : request.getPath();
                    if (body.contains("\"initialize\"")) {
                        return json("{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":"
                                + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                                + "\"serverInfo\":{\"name\":\"proxy-mock\",\"version\":\"1.0.0\"}}}");
                    }
                    if (body.contains("\"tools/list\"")) {
                        return json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                                + tool("echo", false) + "," + tool("idem_tool", true) + ","
                                + tool("always_fail", false) + "," + tool("big", false) + "]}}");
                    }
                    if (body.contains("\"tools/call\"")) {
                        if (path.contains("/mcp/slow")) {
                            return new MockResponse().setResponseCode(200)
                                    .setHeader("Content-Type", "application/json")
                                    .setBodyDelay(4, TimeUnit.SECONDS)
                                    .setBody(callResult("slow"));
                        }
                        if (body.contains("\"idem_tool\"")) {
                            if (IDEM_CALLS.incrementAndGet() == 1) {
                                return new MockResponse().setResponseCode(500).setBody("boom");
                            }
                            return json(callResult("idem-ok"));
                        }
                        if (body.contains("\"always_fail\"")) {
                            return new MockResponse().setResponseCode(500).setBody("boom");
                        }
                        if (body.contains("\"big\"")) {
                            return json(callResult(BIG_TEXT));
                        }
                        return json(callResult("echo: hi"));
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

    private static final java.util.concurrent.atomic.AtomicInteger IDEM_CALLS =
            new java.util.concurrent.atomic.AtomicInteger();

    private static String tool(String name, boolean idempotent) {
        return "{\"name\":\"" + name + "\",\"description\":\"tool\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}},"
                + "\"_meta\":{\"idempotent\":" + idempotent + "}}";
    }

    private static String callResult(String text) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + text.replace("\"", "\\\"") + "\"}],\"isError\":false}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
