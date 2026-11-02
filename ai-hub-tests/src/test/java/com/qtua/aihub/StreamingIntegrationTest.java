package com.qtua.aihub;

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
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-1.7 端到端验收：SSE 流式透传、usage 末块结算、日志与 spend。
 *
 * <p>使用 H2、MockWebServer 与 Testcontainers Redis；无 Docker 时自动跳过。
 * MockWebServer 按 OpenAI SSE 格式返回分块，最后一块携带 usage。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StreamingIntegrationTest {

    private static final String MASTER_KEY = "stream-master";
    private static final int OK = HttpStatus.OK.value();
    private static final BigDecimal CALL_COST = new BigDecimal("0.00002470");
    private static final String SSE_BODY = """
            data: {"id":"x","object":"chat.completion.chunk","model":"upstream-chat","choices":[{"index":0,"delta":{"content":"Hi"}}]}

            data: {"id":"x","object":"chat.completion.chunk","model":"upstream-chat","choices":[],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}

            data: [DONE]

            """;
    private static final AtomicInteger UPSTREAM_STATUS = new AtomicInteger(OK);
    private static final MockWebServer UPSTREAM = startUpstream();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    private RestClient restClient;
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        UPSTREAM_STATUS.set(OK);
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void stopServer() throws IOException {
        UPSTREAM.shutdown();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
        registry.add("gateway.pricing.models.upstream-chat.input-per-million", () -> "0.27");
        registry.add("gateway.pricing.models.upstream-chat.output-per-million", () -> "1.10");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void streams_sse_and_records_usage_and_spend() {
        String plainKey = generateKey("[\"chat\"]");

        List<String> events = webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + plainKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(streamBody())
                .retrieve()
                .bodyToFlux(String.class)
                .collectList()
                .block(Duration.ofSeconds(15));

        assertThat(events).isNotNull();
        assertThat(events).anyMatch(event -> event.contains("\"model\":\"chat\""));
        assertThat(events).contains("[DONE]");

        String requestId = waitForLogByKey(plainKey);
        CallLogDO log = callLogMapper.selectByRequestId(requestId).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getModelAlias()).isEqualTo("chat");
        assertThat(log.getServedAlias()).isEqualTo("chat");
        assertThat(log.getDeploymentModel()).isEqualTo("upstream-chat");
        assertThat(log.getPromptTokens()).isEqualTo(10);
        assertThat(log.getCompletionTokens()).isEqualTo(20);
        assertThat(log.getTotalTokens()).isEqualTo(30);
        assertThat(log.getCost()).isEqualByComparingTo(CALL_COST);

        String keyHash = KeyHasher.sha256Hex(plainKey);
        assertThat(apiKeyMapper.selectByKeyHash(keyHash).orElseThrow().getSpend())
                .isEqualByComparingTo(CALL_COST);
    }

    @Test
    void stream_error_is_emitted_as_error_event_and_logged() {
        UPSTREAM_STATUS.set(HttpStatus.INTERNAL_SERVER_ERROR.value());
        String plainKey = generateKey("[\"chat\"]");

        List<String> events = webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + plainKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(streamBody())
                .retrieve()
                .bodyToFlux(String.class)
                .collectList()
                .block(Duration.ofSeconds(15));

        assertThat(events).isNotNull();
        assertThat(events).anyMatch(event -> event.contains("upstream_error"));
        String requestId = waitForLogByKey(plainKey);
        CallLogDO log = callLogMapper.selectByRequestId(requestId).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }

    private String streamBody() {
        return "{\"model\":\"chat\",\"stream\":true,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    }

    private String generateKey(String modelsJson) {
        ResponseEntity<String> response = restClient.post()
                .uri("/key/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(MASTER_KEY))
                .body("{\"models\":" + modelsJson + ",\"duration\":\"1h\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(response).get("key").asText();
    }

    private String waitForLogByKey(String plainKey) {
        String keyHash = KeyHasher.sha256Hex(plainKey);
        for (int i = 0; i < 60; i++) {
            List<CallLogDO> logs = callLogMapper.selectRecentByKeyId(keyHash, 1);
            if (!logs.isEmpty()) {
                return logs.get(0).getRequestId();
            }
            sleep();
        }
        throw new AssertionError("call log not persisted within timeout, keyHash=" + keyHash);
    }

    private void sleep() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + response.getBody(), ex);
        }
    }

    private static MockWebServer startUpstream() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (UPSTREAM_STATUS.get() != OK) {
                    return new MockResponse().setResponseCode(UPSTREAM_STATUS.get());
                }
                return new MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(SSE_BODY);
            }
        });
        try {
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start mock upstream", ex);
        }
    }
}
