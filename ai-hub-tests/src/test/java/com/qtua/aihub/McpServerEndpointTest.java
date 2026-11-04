package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.6 端到端验收：ai-hub 作为 MCP Server 暴露（LangChain4j 客户端发现 + 调用），
 * 且 REST/MCP 双路径结果一致。
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpServerEndpointTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream-chat",
             "choices":[{"index":0,"message":{"role":"assistant","content":"MCP 你好"},
                         "finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;
    private static final MockWebServer UPSTREAM = startServer();
    private static final MockWebServer MCP_SRV = startMcpServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_mcp_endpoint;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
    }

    @AfterAll
    static void stopServer() throws IOException {
        UPSTREAM.shutdown();
        MCP_SRV.shutdown();
    }

    @Test
    void mcp_client_discovers_and_calls_chat() throws Exception {
        try (McpClient client = newClient()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).extracting(ToolSpecification::name)
                    .contains("chat_completion", "list_models", "prompts_get");

            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name("chat_completion")
                    .arguments("{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                    .build());
            assertThat(result.isError()).isFalse();
            assertThat(result.resultText()).contains("choices", "MCP 你好");
        }
    }

    @Test
    void rest_and_mcp_produce_consistent_results() throws Exception {
        ResponseEntity<String> rest = restClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .retrieve().toEntity(String.class);
        assertThat(rest.getStatusCode()).isEqualTo(HttpStatus.OK);

        String mcpText;
        try (McpClient client = newClient()) {
            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name("chat_completion")
                    .arguments("{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                    .build());
            mcpText = result.resultText();
        }
        String restContent = objectMapper.readTree(rest.getBody())
                .path("choices").get(0).path("message").path("content").asText();
        assertThat(mcpText).contains(restContent);
    }

    @Test
    void prompts_get_tool_returns_rendered_messages() throws Exception {
        restClient.post().uri("/v1/prompts")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"mcp_tpl\",\"content\":\"User: 你好 {{name}}\",\"variables\":[\"name\"]}")
                .retrieve().toEntity(String.class);
        restClient.post().uri("/v1/prompts/mcp_tpl/publish")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"version\":1}")
                .retrieve().toEntity(String.class);

        try (McpClient client = newClient()) {
            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name("prompts_get")
                    .arguments("{\"name\":\"mcp_tpl\",\"variables\":{\"name\":\"御风\"}}")
                    .build());
            assertThat(result.isError()).isFalse();
            assertThat(result.resultText()).contains("你好 御风");
        }
    }

    @Test
    void list_models_tool_returns_chat_alias() throws Exception {
        try (McpClient client = newClient()) {
            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name("list_models")
                    .arguments("{}")
                    .build());
            assertThat(result.isError()).isFalse();
            assertThat(result.resultText()).contains("chat");
        }
    }

    @Test
    void gateway_exposes_registered_server_tools_over_mcp() throws Exception {
        // 注册一个第三方 MCP Server（mock）到 ai-hub 注册表
        ResponseEntity<String> registered = restClient.post()
                .uri("/v1/mcp/servers")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"mock_srv\",\"transport\":\"http\",\"endpoint\":\""
                        + MCP_SRV.url("/mcp").toString() + "\"}")
                .retrieve().toEntity(String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        try (McpClient client = newClient()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).extracting(ToolSpecification::name)
                    .contains("chat_completion", "mock_srv__echo", "mock_srv__add");

            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name("mock_srv__echo")
                    .arguments("{\"message\":\"hi\"}")
                    .build());
            assertThat(result.isError()).isFalse();
            assertThat(result.resultText()).contains("echo: hi");
        }
    }

    private McpClient newClient() {
        return new DefaultMcpClient.Builder()
                .key("aihub-test")
                .transport(new StreamableHttpMcpTransport.Builder()
                        .url("http://localhost:" + port + "/mcp")
                        .customHeaders(Map.of("Authorization", "Bearer " + MASTER_KEY))
                        .timeout(Duration.ofSeconds(10))
                        .build())
                .build();
    }

    private static MockWebServer startMcpServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
                @Override
                public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                    String body = request.getBody().readString(java.nio.charset.StandardCharsets.UTF_8);
                    if (body.contains("\"initialize\"")) {
                        return json("{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":"
                                + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                                + "\"serverInfo\":{\"name\":\"mock_srv\",\"version\":\"1.0.0\"}}}");
                    }
                    if (body.contains("\"tools/list\"")) {
                        return json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                                + "{\"name\":\"echo\",\"description\":\"echo tool\","
                                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"
                                + "{\"name\":\"add\",\"description\":\"add tool\","
                                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}");
                    }
                    if (body.contains("\"tools/call\"")) {
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

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            for (int i = 0; i < 8; i++) {
                server.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(UPSTREAM_RESPONSE));
            }
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
