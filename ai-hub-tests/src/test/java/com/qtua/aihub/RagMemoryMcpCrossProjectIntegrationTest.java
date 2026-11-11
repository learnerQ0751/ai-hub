package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
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

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.6 跨项目记忆授权验收：服务端开关开启后，用户级记忆可按显式请求跨项目读取。
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagMemoryMcpCrossProjectIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String TENANT = "t-cross";
    private static final String USER = "u-cross";
    private static final String PROJECT_A = "p-cross-a";
    private static final String PROJECT_B = "p-cross-b";

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_rag_memory_mcp_cross;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.memory.cross-project-sharing-enabled", () -> "true");
    }

    @Test
    void user_level_memory_is_shared_across_projects_when_enabled() throws Exception {
        try (McpClient client = newClient()) {
            callTool(client, "save_memory", "{\"tenant_id\":\"" + TENANT + "\","
                    + "\"project_id\":\"" + PROJECT_A + "\",\"user_id\":\"" + USER + "\","
                    + "\"content\":\"用户画像：偏好中文\"}");

            JsonNode isolated = callTool(client, "recall_memory", "{\"tenant_id\":\"" + TENANT
                    + "\",\"project_id\":\"" + PROJECT_B + "\",\"user_id\":\"" + USER + "\"}");
            assertThat(isolated.path("count").asInt()).isZero();

            JsonNode shared = callTool(client, "recall_memory", "{\"tenant_id\":\"" + TENANT
                    + "\",\"project_id\":\"" + PROJECT_B + "\",\"user_id\":\"" + USER + "\","
                    + "\"cross_project\":true}");
            assertThat(shared.path("count").asInt()).isEqualTo(1);
            assertThat(shared.path("memories").get(0).path("project_id").asText())
                    .isEqualTo(PROJECT_A);
            assertThat(shared.path("memories").get(0).path("content").asText())
                    .isEqualTo("用户画像：偏好中文");

            JsonNode otherTenant = callTool(client, "recall_memory", "{\"tenant_id\":\"t-other\","
                    + "\"project_id\":\"" + PROJECT_B + "\",\"user_id\":\"" + USER + "\","
                    + "\"cross_project\":true}");
            assertThat(otherTenant.path("count").asInt()).isZero();
        }
    }

    @Test
    void session_level_memory_is_never_shared_across_projects() throws Exception {
        try (McpClient client = newClient()) {
            callTool(client, "save_memory", "{\"tenant_id\":\"" + TENANT + "\","
                    + "\"project_id\":\"" + PROJECT_A + "\",\"user_id\":\"" + USER + "\","
                    + "\"session_id\":\"s-cross\",\"content\":\"会话内容\"}");
        }

        JsonNode rejected = rpc("{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"recall_memory\",\"arguments\":{\"tenant_id\":\"" + TENANT
                + "\",\"project_id\":\"" + PROJECT_B + "\",\"user_id\":\"" + USER + "\","
                + "\"session_id\":\"s-cross\",\"cross_project\":true}}}");
        assertThat(rejected.path("error").path("message").asText()).contains("会话");
    }

    private McpClient newClient() {
        return new DefaultMcpClient.Builder()
                .key("aihub-req36-cross-test")
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
        ResponseEntity<String> response = restClient.post()
                .uri("/mcp")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(response.getBody());
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + body, ex);
        }
    }
}
