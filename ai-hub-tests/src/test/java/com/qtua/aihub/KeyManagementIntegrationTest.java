package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

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
@SpringBootTest(classes = GatewayApplication.class,
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

    @Autowired
    private com.qtua.aihub.api.KeyService keyService;

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
    void master_key_is_rejected_on_data_plane() {
        ResponseEntity<String> response = post("/v1/chat/completions", MASTER_KEY, REASONER_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readJson(response).get("error").get("type").asText())
                .isEqualTo("authorization_error");
    }

    @Test
    void blocked_key_is_rejected_and_unblock_restores_access() {
        String plainKey = generateKey("[\"chat\"]");

        // 封禁/解封端点属于控制面；数据面测试直接调用 KeyService。
        keyService.block(plainKey);
        ResponseEntity<String> blockedChat = post("/v1/chat/completions", plainKey, CHAT_BODY);
        keyService.unblock(plainKey);
        ResponseEntity<String> restoredChat = post("/v1/chat/completions", plainKey, CHAT_BODY);

        assertThat(blockedChat.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readJson(blockedChat).get("error").get("type").asText()).isEqualTo("auth_error");
        assertThat(readJson(blockedChat).get("error").get("message").asText()).contains("封禁");
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
        assertThat(readJson(response).get("error").get("message").asText()).contains("过期");
    }

    @Test
    void key_info_returns_spend_models_and_masks_key() {
        String plainKey = generateKey("[\"chat\"]");

        com.qtua.aihub.api.model.res.KeyInfoResDTO body = keyService.info(plainKey);

        assertThat(body.key()).startsWith("sk-****").endsWith(lastFour(plainKey));
        assertThat(body.key()).isNotEqualTo(plainKey);
        assertThat(body.info().spend()).isZero();
        assertThat(body.info().models().get(0)).isEqualTo("chat");
    }

    @Test
    void management_endpoints_are_not_exposed_on_data_plane() {
        String plainKey = generateKey("[\"chat\"]");

        ResponseEntity<String> response = post("/key/generate", plainKey, "{\"models\":[]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readJson(response).get("error").get("type").asText()).isEqualTo("not_found");
    }

    @Test
    void unknown_key_is_rejected() {
        ResponseEntity<String> response = post("/v1/chat/completions", "sk-unknown", CHAT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readJson(response).get("error").get("type").asText()).isEqualTo("auth_error");
    }

    private String generateKey(String modelsJson) {
        try {
            java.util.List<String> models = objectMapper.readValue(modelsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                    });
            String plainKey = TestKeys.create(keyService, "test-tenant", "test-project", models);
            assertThat(plainKey).startsWith("sk-");
            return plainKey;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("invalid models json", ex);
        }
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
