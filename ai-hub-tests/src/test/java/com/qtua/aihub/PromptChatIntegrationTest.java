package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.qtua.aihub.api.KeyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.5 端到端验收：Prompt 渲染（不调模型）与对话（渲染→chat 链路→治理日志）。
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PromptChatIntegrationTest {

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

    private static final String ADMIN_KEY = "integration-master";
    private static final MockWebServer UPSTREAM = startServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private com.qtua.aihub.manager.prompt.PromptManager promptManager;

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_prompt_chat;"
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
    }

    @Test
    void render_outputs_messages_without_calling_model() throws Exception {
        createAndPublish("render_tpl", "System: 你是助手\nUser: 介绍 {{name}}", List.of("name"));
        int upstreamCallsBefore = UPSTREAM.getRequestCount();

        ResponseEntity<String> response = render("render_tpl",
                "{\"variables\":{\"name\":\"御风\"}}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode messages = objectMapper.readTree(response.getBody()).path("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("你是助手");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).path("content").asText()).isEqualTo("介绍 御风");
        assertThat(UPSTREAM.getRequestCount()).isEqualTo(upstreamCallsBefore);
    }

    @Test
    void render_missing_variable_returns_400() throws Exception {
        createAndPublish("render_bad", "User: 你好 {{name}}", List.of("name"));
        ResponseEntity<String> response = render("render_bad", "{\"variables\":{}}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("name");
    }

    @Test
    void chat_renders_calls_model_and_logs_prompt_version() throws Exception {
        createAndPublish("chat_tpl", "System: 你是助手\nUser: 介绍 {{name}}", List.of("name"));

        ResponseEntity<String> response = restClient.post()
                .uri("/v1/prompts/chat_tpl/chat")
                .header("Authorization", "Bearer " + dataKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"model\":\"chat\",\"variables\":{\"name\":\"御风\"}}")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("choices");

        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(log.getCallType()).isEqualTo("PROMPT");
        assertThat(log.getTargetName()).isEqualTo("chat_tpl@v1");
        assertThat(log.getModelAlias()).isEqualTo("chat");
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getTotalTokens()).isEqualTo(2);
    }

    @Test
    void chat_unknown_version_returns_404() throws Exception {
        createAndPublish("chat_v", "User: hi", List.of());
        ResponseEntity<String> response = restClient.post()
                .uri("/v1/prompts/chat_v/chat")
                .header("Authorization", "Bearer " + dataKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"model\":\"chat\",\"version\":9,\"variables\":{}}")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * 控制面管理端点不在数据面；测试直接通过 PromptManager 造已发布模板。
     */
    private void createAndPublish(String name, String content, List<String> variables) {
        promptManager.save(new com.qtua.aihub.manager.model.PromptTemplateBO(name, 1, content,
                variables, "DRAFT", "test-tenant", "test", java.time.Instant.now()));
        assertThat(promptManager.publishVersion("test-tenant", name, 1)).isTrue();
    }

    private ResponseEntity<String> render(String name, String body) {
        return restClient.post()
                .uri("/v1/prompts/" + name + "/render")
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
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"upstream-chat\","
                            + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                            + "\"content\":\"你好\"},\"finish_reason\":\"stop\"}],"
                            + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,"
                            + "\"total_tokens\":2}}"));
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
