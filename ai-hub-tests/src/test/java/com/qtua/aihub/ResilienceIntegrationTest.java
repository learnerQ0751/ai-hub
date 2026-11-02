package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-1.6 端到端验收：主备切换、别名级 fallback、冷却与不可重试错误。
 *
 * <p>使用 H2、MockWebServer 与 Testcontainers Redis；无 Docker 时自动跳过。
 * 每个场景使用独立别名，避免 Redis 冷却状态互相干扰。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResilienceIntegrationTest {

    private static final String MASTER_KEY = "resilience-master";
    private static final int DEFAULT_PRIMARY_STATUS = HttpStatus.UNAUTHORIZED.value();
    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}
            """;

    /** 主部署上游状态：默认 401（模拟错误 api-key），4xx 场景改为 400。 */
    private static final AtomicInteger PRIMARY_STATUS = new AtomicInteger(DEFAULT_PRIMARY_STATUS);
    private static final AtomicInteger PRIMARY_HITS = new AtomicInteger();
    private static final MockWebServer PRIMARY = startPrimary();
    private static final MockWebServer BACKUP = startBackup();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        PRIMARY_STATUS.set(DEFAULT_PRIMARY_STATUS);
        PRIMARY_HITS.set(0);
        // 清理上一轮测试的冷却状态，保证用例可重复运行。
        Set<String> keys = redisTemplate.keys("{resilience}:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void stopServers() throws IOException {
        PRIMARY.shutdown();
        BACKUP.shutdown();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("gateway.resilience.num-retries", () -> "1");
        registry.add("gateway.resilience.allowed-fails", () -> "1");
        registry.add("gateway.resilience.cooldown-seconds", () -> "60");

        // 场景 1：同别名主备（主 401，备 200）。
        addModel(registry, "chat-a", 0, PRIMARY, "primary-model-a", "0");
        addModel(registry, "chat-a", 1, BACKUP, "backup-model-a", "1");
        // 场景 2：别名级 fallback（chat-b → chat-b-backup）。
        addModel(registry, "chat-b", 0, PRIMARY, "primary-model-b", "0");
        addModel(registry, "chat-b-backup", 0, BACKUP, "backup-model-b", "0");
        registry.add("gateway.fallbacks.chat-b[0]", () -> "chat-b-backup");
        // 场景 3：冷却验证（主冷却后第二次请求不再打主）。
        addModel(registry, "chat-c", 0, PRIMARY, "primary-model-c", "0");
        addModel(registry, "chat-c", 1, BACKUP, "backup-model-c", "1");
        // 场景 4：上游 400 不可重试。
        addModel(registry, "chat-d", 0, PRIMARY, "primary-model-d", "0");
    }

    @Test
    void switches_to_backup_priority_deployment_on_upstream_auth_error() {
        ResponseEntity<String> response = post("chat-a");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(response).get("model").asText()).isEqualTo("chat-a");
        CallLogDO log = awaitLog(response);
        assertThat(log.getServedAlias()).isEqualTo("chat-a");
        assertThat(log.getDeploymentModel()).isEqualTo("backup-model-a");
        assertThat(log.getRetryCount()).isEqualTo(1);
        assertThat(log.isFallbackUsed()).isFalse();
        assertThat(PRIMARY_HITS.get()).isEqualTo(1);
    }

    @Test
    void falls_back_to_configured_alias_and_rewrites_model() {
        ResponseEntity<String> response = post("chat-b");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(response).get("model").asText()).isEqualTo("chat-b");
        CallLogDO log = awaitLog(response);
        assertThat(log.getServedAlias()).isEqualTo("chat-b-backup");
        assertThat(log.getDeploymentModel()).isEqualTo("backup-model-b");
        assertThat(log.getRetryCount()).isEqualTo(2);
        assertThat(log.isFallbackUsed()).isTrue();
    }

    @Test
    void cooling_down_deployment_is_not_selected_by_later_requests() {
        ResponseEntity<String> first = post("chat-c");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(PRIMARY_HITS.get()).isEqualTo(1);
        int backupHitsAfterFirst = BACKUP.getRequestCount();

        ResponseEntity<String> second = post("chat-c");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(PRIMARY_HITS.get()).isEqualTo(1);
        assertThat(BACKUP.getRequestCount()).isEqualTo(backupHitsAfterFirst + 1);
        CallLogDO log = awaitLog(second);
        assertThat(log.getRetryCount()).isZero();
        assertThat(log.getDeploymentModel()).isEqualTo("backup-model-c");
    }

    @Test
    void non_retryable_upstream_error_is_not_retried() {
        PRIMARY_STATUS.set(HttpStatus.BAD_REQUEST.value());

        ResponseEntity<String> response = post("chat-d");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(PRIMARY_HITS.get()).isEqualTo(1);
        CallLogDO log = awaitLog(response);
        assertThat(log.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(log.getRetryCount()).isZero();
        assertThat(log.isFallbackUsed()).isFalse();
    }

    private ResponseEntity<String> post(String model) {
        String body = "{\"model\":\"" + model
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
        return restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(MASTER_KEY))
                .body(body)
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

    private CallLogDO awaitLog(ResponseEntity<String> response) {
        String requestId = response.getHeaders().getFirst("X-Request-Id");
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

    private static void addModel(DynamicPropertyRegistry registry, String alias, int index,
                                 MockWebServer server, String model, String priority) {
        String prefix = "gateway.model-list." + alias + "[" + index + "].";
        registry.add(prefix + "api-base", () -> server.url("/").toString());
        registry.add(prefix + "api-key", () -> "test-key");
        registry.add(prefix + "model", () -> model);
        registry.add(prefix + "priority", () -> priority);
    }

    private static MockWebServer startPrimary() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                PRIMARY_HITS.incrementAndGet();
                return new MockResponse().setResponseCode(PRIMARY_STATUS.get());
            }
        });
        return start(server);
    }

    private static MockWebServer startBackup() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(UPSTREAM_RESPONSE);
            }
        });
        return start(server);
    }

    private static MockWebServer start(MockWebServer server) {
        try {
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start mock upstream", ex);
        }
    }
}
