package com.qtua.aihub;

import com.qtua.aihub.console.ConsoleApplication;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.1 端到端验收：MCP Server 注册、列表、探活、重探、注销、名称冲突与掩码。
 *
 * <p>HTTP MCP Server 用 MockWebServer 模拟（JSON-RPC initialize）；stdio 用独立 JVM 进程
 * {@link com.qtua.aihub.mcp.InMemoryStdioServer}。</p>
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpRegistryIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String INITIALIZE_RESPONSE = """
            {"jsonrpc":"2.0","id":0,"result":{"protocolVersion":"2025-06-18",
             "capabilities":{"tools":{}},"serverInfo":{"name":"mock-http","version":"1.0.0"}}}
            """;

    private static final MockWebServer HTTP_MCP = startServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        // 测试需要断言 4xx/5xx 状态码与错误体，关闭默认的异常抛出。
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
    }

    @AfterAll
    static void stopServer() throws IOException {
        HTTP_MCP.shutdown();
    }

    @Test
    void registers_http_and_stdio_servers_and_lists_them() {
        ResponseEntity<String> http = register(Map.of(
                "name", "mock_http",
                "transport", "http",
                "endpoint", HTTP_MCP.url("/mcp").toString(),
                "description", "HTTP mock"));
        assertThat(http.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> stdio = register(Map.of(
                "name", "mock_stdio",
                "transport", "stdio",
                "command", javaExecutable(),
                "args", java.util.List.of("-cp", System.getProperty("java.class.path"),
                        "com.qtua.aihub.mcp.InMemoryStdioServer"),
                "description", "stdio mock"));
        assertThat(stdio.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> list = get("/v1/mcp/servers");
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody())
                .contains("mock_http", "mock_stdio", "ACTIVE");
    }

    @Test
    void unreachable_http_server_registered_as_down_with_error() {
        ResponseEntity<String> response = register(Map.of(
                "name", "dead_http",
                "transport", "http",
                "endpoint", "http://127.0.0.1:1/mcp"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("DOWN", "lastError");
    }

    @Test
    void duplicate_name_is_idempotent_and_double_underscore_rejected() throws Exception {
        ResponseEntity<String> first = register(Map.of("name", "dup_server", "transport", "http",
                "endpoint", HTTP_MCP.url("/mcp").toString()));
        ResponseEntity<String> dup = register(Map.of("name", "dup_server", "transport", "http",
                "endpoint", HTTP_MCP.url("/mcp").toString()));
        // REQ-5.2：完全相同的重复注册按幂等重放处理。
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(dup.getBody()).contains(objectMapper.readTree(first.getBody())
                .path("serverId").asText());

        ResponseEntity<String> conflict = register(Map.of("name", "dup_server", "transport",
                "http", "endpoint", HTTP_MCP.url("/mcp/other").toString()));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> badName = register(Map.of("name", "bad__name", "transport", "http",
                "endpoint", HTTP_MCP.url("/mcp").toString()));
        assertThat(badName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badName.getBody()).contains("__");
    }

    @Test
    void delete_removes_server_and_missing_returns_404() throws Exception {
        ResponseEntity<String> created = register(Map.of("name", "to_delete", "transport", "http",
                "endpoint", HTTP_MCP.url("/mcp").toString()));
        String serverId = objectMapper.readTree(created.getBody()).path("serverId").asText();

        ResponseEntity<String> deleted = delete("/v1/mcp/servers/" + serverId);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/v1/mcp/servers").getBody()).doesNotContain("to_delete");

        ResponseEntity<String> missing = delete("/v1/mcp/servers/" + serverId);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reprobe_returns_latest_status() throws Exception {
        register(Map.of("name", "dead_reprobe", "transport", "http",
                "endpoint", "http://127.0.0.1:1/mcp"));
        String serverId = findServerIdByName("dead_reprobe");

        ResponseEntity<String> reprobe = restClient.post()
                .uri("/v1/mcp/servers/{id}/reprobe", serverId)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve()
                .toEntity(String.class);
        assertThat(reprobe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reprobe.getBody()).contains("DOWN", "lastError");
    }

    @Test
    void endpoint_secrets_are_masked() {
        ResponseEntity<String> response = register(Map.of(
                "name", "masked_server",
                "transport", "http",
                "endpoint", "http://user:pass@127.0.0.1:1/mcp?api_key=secret-token"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).doesNotContain("pass", "secret-token");
    }

    @Test
    void virtual_key_cannot_access_management_api() throws Exception {
        ResponseEntity<String> generated = restClient.post()
                .uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"models\":[\"chat\"]}")
                .retrieve()
                .toEntity(String.class);
        String virtualKey = objectMapper.readTree(generated.getBody()).path("key").asText();

        ResponseEntity<String> list = restClient.get()
                .uri("/v1/mcp/servers")
                .header("Authorization", "Bearer " + virtualKey)
                .retrieve()
                .toEntity(String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(list.getBody()).contains("authorization_error");
    }

    @Test
    void runtime_stats_endpoint_returns_counters() {
        ResponseEntity<String> response = get("/v1/mcp/runtime/stats");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("activeStdioProcesses",
                "inFlightProbeHttp", "inFlightToolCalls");
    }

    private ResponseEntity<String> register(Map<String, Object> body) {
        return restClient.post()
                .uri("/v1/mcp/servers")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> get(String uri) {
        return restClient.get().uri(uri)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> delete(String uri) {
        return restClient.delete().uri(uri)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
    }

    private String findServerIdByName(String name) throws Exception {
        com.fasterxml.jackson.databind.JsonNode list =
                objectMapper.readTree(get("/v1/mcp/servers").getBody());
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (name.equals(node.path("name").asText())) {
                return node.path("serverId").asText();
            }
        }
        throw new IllegalStateException("server not found: " + name);
    }

    private static String javaExecutable() {
        String home = System.getProperty("java.home");
        String exe = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return home + java.io.File.separator + "bin" + java.io.File.separator + exe;
    }

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String body = request.getBody().readString(StandardCharsets.UTF_8);
                    if (body.contains("\"initialize\"")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(INITIALIZE_RESPONSE);
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
}
