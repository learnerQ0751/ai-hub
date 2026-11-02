package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.util.KeyHasher;
import com.qtua.aihub.dal.mapper.ApiKeyMapper;
import com.qtua.aihub.dal.model.ApiKeyDO;
import com.qtua.aihub.manager.ApiKeyManager;
import com.qtua.aihub.manager.model.ApiKeyBO;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-1.3 端到端验收：生成、鉴权、白名单、封禁/解封、过期、/key/info。
 *
 * <p>使用 H2（PostgreSQL 兼容模式）与 MockWebServer，不依赖本机 Docker/真实上游。</p>
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KeyManagementIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String CHAT_BODY =
            "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String REASONER_BODY =
            "{\"model\":\"reasoner\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private static final MockWebServer UPSTREAM = startServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    @Autowired
    private ApiKeyManager apiKeyManager;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
        registry.add("gateway.model-list.reasoner[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.reasoner[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.reasoner[0].model", () -> "upstream-reasoner");
    }

    @AfterAll
    static void stopServer() throws IOException {
        UPSTREAM.shutdown();
    }

    @Test
    void generate_returns_plain_key_and_database_stores_only_hash() {
        String plainKey = generateKey("[\"chat\"]");

        assertThat(apiKeyMapper.selectByKeyHash(plainKey)).isEmpty();
        ApiKeyDO stored = apiKeyMapper.selectByKeyHash(KeyHasher.sha256Hex(plainKey)).orElseThrow();
        assertThat(stored.getKeyHash()).isEqualTo(KeyHasher.sha256Hex(plainKey));
        assertThat(stored.getModels()).contains("chat");
    }

    @Test
    void virtual_key_calls_authorized_model() {
        String plainKey = generateKey("[\"chat\"]");

        ResponseEntity<String> response = post("/v1/chat/completions", plainKey, CHAT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"model\":\"chat\"", "\"usage\"");
    }

    @Test
    void virtual_key_is_denied_for_unauthorized_model() {
        String plainKey = generateKey("[\"chat\"]");

        ResponseEntity<String> response = post("/v1/chat/completions", plainKey, REASONER_BODY);

        JsonNode error = readJson(response).get("error");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(error.get("type").asText()).isEqualTo("key_model_access_denied");
        assertThat(error.get("param").asText()).isEqualTo("model");
        assertThat(error.get("code").asText()).isEqualTo("403");
    }

    @Test
    void master_key_can_call_any_model() {
        ResponseEntity<String> response = post("/v1/chat/completions", MASTER_KEY, REASONER_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"model\":\"reasoner\"");
    }

    @Test
    void blocked_key_is_rejected_and_unblock_restores_access() {
        String plainKey = generateKey("[\"chat\"]");

        ResponseEntity<String> blockResponse = post("/key/block", MASTER_KEY,
                "{\"key\":\"" + plainKey + "\"}");
        ResponseEntity<String> blockedChat = post("/v1/chat/completions", plainKey, CHAT_BODY);
        ResponseEntity<String> unblockResponse = post("/key/unblock", MASTER_KEY,
                "{\"key\":\"" + plainKey + "\"}");
        ResponseEntity<String> restoredChat = post("/v1/chat/completions", plainKey, CHAT_BODY);

        assertThat(blockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blockedChat.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readJson(blockedChat).get("error").get("type").asText()).isEqualTo("auth_error");
        assertThat(readJson(blockedChat).get("error").get("message").asText()).contains("blocked");
        assertThat(unblockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoredChat.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expired_key_is_rejected() {
        String plainKey = "sk-expired-" + UUID.randomUUID();
        ApiKeyBO expired = new ApiKeyBO(KeyHasher.sha256Hex(plainKey), List.of("chat"),
                BigDecimal.ZERO, null, null, null, null,
                Instant.now().minusSeconds(60), false, Map.of(), Instant.now());
        apiKeyManager.save(expired);

        ResponseEntity<String> response = post("/v1/chat/completions", plainKey, CHAT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readJson(response).get("error").get("message").asText()).contains("expired");
    }

    @Test
    void key_info_returns_spend_models_and_masks_key() {
        String plainKey = generateKey("[\"chat\"]");

        ResponseEntity<String> response = get("/key/info?key=" + plainKey, MASTER_KEY);

        JsonNode body = readJson(response);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.get("key").asText()).startsWith("sk-****").endsWith(lastFour(plainKey));
        assertThat(body.get("key").asText()).isNotEqualTo(plainKey);
        assertThat(body.get("info").get("spend").decimalValue()).isZero();
        assertThat(body.get("info").get("models").get(0).asText()).isEqualTo("chat");
        assertThat(response.getBody()).doesNotContain(plainKey);
    }

    @Test
    void virtual_key_cannot_use_management_endpoint() {
        String plainKey = generateKey("[\"chat\"]");

        ResponseEntity<String> response = post("/key/generate", plainKey, "{\"models\":[]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readJson(response).get("error").get("type").asText()).isEqualTo("auth_error");
    }

    @Test
    void unknown_key_is_rejected() {
        ResponseEntity<String> response = post("/v1/chat/completions", "sk-unknown", CHAT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readJson(response).get("error").get("type").asText()).isEqualTo("auth_error");
    }

    private String generateKey(String modelsJson) {
        String body = "{\"models\":" + modelsJson + ",\"duration\":\"1h\","
                + "\"metadata\":{\"user\":\"integration\"}}";
        ResponseEntity<String> response = post("/key/generate", MASTER_KEY, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = readJson(response);
        assertThat(json.get("key").asText()).startsWith("sk-");
        assertThat(json.get("expires").asText()).isNotBlank();
        return json.get("key").asText();
    }

    private ResponseEntity<String> post(String path, String token, String body) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token))
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, response) -> {
                })
                .toEntity(String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        return restClient.get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {
                })
                .toEntity(String.class);
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + response.getBody(), ex);
        }
    }

    private String lastFour(String value) {
        return value.substring(value.length() - 4);
    }

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(UPSTREAM_RESPONSE);
            }
        });
        try {
            server.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start mock upstream", ex);
        }
        return server;
    }
}
