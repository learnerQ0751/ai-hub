package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.qtua.aihub.api.KeyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.2 端到端验收：工具发现聚合、缓存/刷新、降级、过滤与注销失效。
 *
 * <p>HTTP MCP Server 用 MockWebServer 模拟；每个 Server 通过路径区分（/mcp/a、/mcp/b），
 * dispatcher 支持 initialize 与 tools/list，并统计 tools/list 调用次数。</p>
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpToolAggregationTest {

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
    private static final AtomicInteger TOOLS_LIST_CALLS = new AtomicInteger();
    private static final AtomicReference<List<String>> B_TOOLS =
            new AtomicReference<>(List.of("search", "fetch"));
    private static final MockWebServer HTTP_MCP = startServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        // 测试间共享静态 mock 状态，逐用例重置，避免相互影响。
        B_TOOLS.set(List.of("search", "fetch"));
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        // 独立 H2 库：工具聚合是全表 ACTIVE 扫描，避免与其他测试类注册的 Server 交叉污染。
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_tools;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> ADMIN_KEY);
    }

    @AfterAll
    static void stopServer() throws IOException {
        HTTP_MCP.shutdown();
    }

    @Test
    void aggregates_tools_from_active_servers_with_caching_and_refresh() throws Exception {
        register("agg_a", HTTP_MCP.url("/mcp/a").toString());
        register("agg_b", HTTP_MCP.url("/mcp/b").toString());

        int callsBefore = TOOLS_LIST_CALLS.get();
        ResponseEntity<String> first = listTools(null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode tools = objectMapper.readTree(first.getBody()).path("tools");
        assertThat(tools.findValuesAsText("name"))
                .contains("agg_a__echo", "agg_a__add", "agg_b__search", "agg_b__fetch");
        int callsAfterFirst = TOOLS_LIST_CALLS.get();
        assertThat(callsAfterFirst - callsBefore).isEqualTo(2);

        // TTL 内重复请求：不重复访问上游。
        ResponseEntity<String> second = listTools(null);
        assertThat(objectMapper.readTree(second.getBody()).path("tools")).hasSize(4);
        assertThat(TOOLS_LIST_CALLS.get()).isEqualTo(callsAfterFirst);

        // 手动刷新：重新聚合。
        ResponseEntity<String> refreshed = refresh();
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TOOLS_LIST_CALLS.get()).isEqualTo(callsAfterFirst + 2);

        // 上游工具变更 → 刷新可见。
        B_TOOLS.set(List.of("search", "fetch_v2"));
        refresh();
        assertThat(objectMapper.readTree(listTools(null).getBody()).path("tools")
                .findValuesAsText("name")).contains("agg_b__fetch_v2")
                .doesNotContain("agg_b__fetch");

        cleanup("agg_a", "agg_b");
    }

    @Test
    void unreachable_server_is_degraded_but_others_return() throws Exception {
        register("deg_ok", HTTP_MCP.url("/mcp/a").toString());
        // 探活成功（ACTIVE），但 tools/list 返回 500 → 聚合时降级。
        register("deg_dead", HTTP_MCP.url("/mcp/slow").toString());
        refresh();

        ResponseEntity<String> response = listTools(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("tools").findValuesAsText("name"))
                .contains("deg_ok__echo", "deg_ok__add");
        assertThat(body.path("degradedServers").toString()).contains("deg_dead");

        cleanup("deg_ok", "deg_dead");
    }

    @Test
    void filters_by_server_id_and_name() throws Exception {
        String idA = register("f_a", HTTP_MCP.url("/mcp/a").toString());
        register("f_b", HTTP_MCP.url("/mcp/b").toString());
        refresh();

        ResponseEntity<String> byId = listTools("?server_id=" + idA);
        assertThat(objectMapper.readTree(byId.getBody()).path("tools").findValuesAsText("name"))
                .contains("f_a__echo", "f_a__add")
                .doesNotContain("f_b__search");

        ResponseEntity<String> byName = listTools("?server_id=f_b");
        assertThat(objectMapper.readTree(byName.getBody()).path("tools").findValuesAsText("name"))
                .contains("f_b__search", "f_b__fetch");

        cleanup("f_a", "f_b");
    }

    @Test
    void unregister_invalidates_cache() throws Exception {
        register("inv_a", HTTP_MCP.url("/mcp/a").toString());
        register("inv_b", HTTP_MCP.url("/mcp/b").toString());
        assertThat(objectMapper.readTree(listTools(null).getBody()).path("tools"))
                .hasSize(4);

        deleteByName("inv_b");
        ResponseEntity<String> after = listTools(null);
        assertThat(objectMapper.readTree(after.getBody()).path("tools").findValuesAsText("name"))
                .contains("inv_a__echo")
                .doesNotContain("inv_b__search");

        cleanup("inv_a");
    }

    private String register(String name, String endpoint) {
        return McpTestSupport.register(mcpServerManager, mcpToolCatalog, name, endpoint,
                "test-tenant");
    }

    private ResponseEntity<String> listTools(String query) {
        return restClient.get()
                .uri("/v1/mcp/tools" + (query == null ? "" : query))
                .header("Authorization", "Bearer " + dataKey)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> refresh() {
        return restClient.post()
                .uri("/v1/mcp/tools/refresh")
                .header("Authorization", "Bearer " + dataKey)
                .retrieve().toEntity(String.class);
    }

    private void deleteByName(String name) {
        McpTestSupport.delete(mcpServerManager, name);
        mcpToolCatalog.invalidate();
    }

    private void cleanup(String... names) throws Exception {
        for (String name : names) {
            deleteByName(name);
        }
    }

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String body = request.getBody().readString(StandardCharsets.UTF_8);
                    if (body.contains("\"initialize\"")) {
                        return json("{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":"
                                + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                                + "\"serverInfo\":{\"name\":\"mock\",\"version\":\"1.0.0\"}}}");
                    }
                    if (body.contains("\"tools/list\"")) {
                        TOOLS_LIST_CALLS.incrementAndGet();
                        String path = request.getPath() == null ? "" : request.getPath();
                        if (path.contains("/mcp/slow")) {
                            return new MockResponse().setResponseCode(500).setBody("boom");
                        }
                        List<String> tools = path.contains("/mcp/a")
                                ? List.of("echo", "add") : B_TOOLS.get();
                        return json(toolsListResponse(tools));
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

    private static String toolsListResponse(List<String> names) {
        StringBuilder tools = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                tools.append(',');
            }
            tools.append("{\"name\":\"").append(names.get(i))
                    .append("\",\"description\":\"tool ").append(names.get(i))
                    .append("\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}");
        }
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[" + tools + "]}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
