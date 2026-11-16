package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.qtua.aihub.api.KeyService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.util.KeyHasher;
import com.qtua.aihub.dal.mapper.ApiKeyMapper;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-1.5 端到端验收：调用日志、Token 计量、spend 累加、失败留痕与价格缺失。
 *
 * <p>使用 H2（PostgreSQL 兼容模式）与 MockWebServer，不依赖本机 Docker/真实上游。
 * chat 配置价格，reasoner 故意不配价格以验证「告警且 cost=0」。</p>
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CallLogIntegrationTest {

    @Autowired
    private KeyService keyService;

    private static final String ADMIN_KEY = "integration-master";
    private static final String CHAT_BODY =
            "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String REASONER_BODY =
            "{\"model\":\"reasoner\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream-chat",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}
            """;
    /** 10 * 0.27/1M + 20 * 1.10/1M。 */
    private static final BigDecimal CALL_COST = new BigDecimal("0.00002470");

    private static final MockWebServer UPSTREAM = startServer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> ADMIN_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
        registry.add("gateway.model-list.reasoner[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.reasoner[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.reasoner[0].model", () -> "upstream-reasoner");
        registry.add("gateway.pricing.models.upstream-chat.input-per-million", () -> "0.27");
        registry.add("gateway.pricing.models.upstream-chat.output-per-million", () -> "1.10");
    }

    @AfterAll
    static void stopServer() throws IOException {
        UPSTREAM.shutdown();
    }

    @Test
    void successful_call_writes_one_log_and_accumulates_spend() {
        String plainKey = generateKey(List.of("chat"));

        ResponseEntity<String> response = post("/v1/chat/completions", plainKey, CHAT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(requestId).isNotBlank();

        CallLogDO log = awaitLog(requestId);
        assertThat(log.getKeyId()).isEqualTo(KeyHasher.sha256Hex(plainKey));
        assertThat(log.getModelAlias()).isEqualTo("chat");
        assertThat(log.getDeploymentModel()).isEqualTo("upstream-chat");
        assertThat(log.getPromptTokens()).isEqualTo(10);
        assertThat(log.getCompletionTokens()).isEqualTo(20);
        assertThat(log.getTotalTokens()).isEqualTo(30);
        assertThat(log.getCost()).isEqualByComparingTo(CALL_COST);
        assertThat(log.getLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getServedAlias()).isEqualTo("chat");
        assertThat(log.getRetryCount()).isZero();
        assertThat(log.isFallbackUsed()).isFalse();
        assertThat(callLogMapper.countByKeyId(KeyHasher.sha256Hex(plainKey))).isEqualTo(1);

        assertThat(apiKeyMapper.selectByKeyHash(KeyHasher.sha256Hex(plainKey)).orElseThrow().getSpend())
                .isEqualByComparingTo(CALL_COST);
    }

    @Test
    void two_calls_accumulate_spend_and_key_info_reflects_it() {
        String plainKey = generateKey(List.of("chat"));

        ResponseEntity<String> first = post("/v1/chat/completions", plainKey, CHAT_BODY);
        ResponseEntity<String> second = post("/v1/chat/completions", plainKey, CHAT_BODY);
        awaitLog(first.getHeaders().getFirst("X-Request-Id"));
        awaitLog(second.getHeaders().getFirst("X-Request-Id"));

        String keyHash = KeyHasher.sha256Hex(plainKey);
        assertThat(apiKeyMapper.selectByKeyHash(keyHash).orElseThrow().getSpend())
                .isEqualByComparingTo(CALL_COST.multiply(BigDecimal.valueOf(2)));

        assertThat(keyService.info(plainKey).info().spend())
                .isEqualByComparingTo(CALL_COST.multiply(BigDecimal.valueOf(2)));
    }

    @Test
    void denied_call_is_logged_with_status_403() {
        String plainKey = generateKey(List.of("chat"));

        ResponseEntity<String> response = post("/v1/chat/completions", plainKey, REASONER_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(log.getStatus()).isEqualTo(403);
        assertThat(log.getDeploymentModel()).isNull();
        assertThat(log.getCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(apiKeyMapper.selectByKeyHash(KeyHasher.sha256Hex(plainKey)).orElseThrow().getSpend())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void missing_price_records_zero_cost_and_no_spend() {
        String plainKey = generateKey(List.of("reasoner"));

        ResponseEntity<String> response = post("/v1/chat/completions", plainKey, REASONER_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(log.getDeploymentModel()).isEqualTo("upstream-reasoner");
        assertThat(log.getCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(apiKeyMapper.selectByKeyHash(KeyHasher.sha256Hex(plainKey)).orElseThrow().getSpend())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void master_key_is_rejected_on_data_plane() {
        ResponseEntity<String> response = post("/v1/chat/completions", ADMIN_KEY, CHAT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readJson(response).path("error").path("type").asText())
                .isEqualTo("authorization_error");
    }

    /**
     * 等待异步日志落库（最多 2.5 秒）。
     *
     * @param requestId 请求追踪 ID
     * @return 日志
     */
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

    private String generateKey(List<String> models) {
        return TestKeys.create(keyService, "test-tenant", "test-project", models);
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
