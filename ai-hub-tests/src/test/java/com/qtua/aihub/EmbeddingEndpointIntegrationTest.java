package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import com.qtua.aihub.api.KeyService;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.7 /v1/embeddings 端到端：OpenAI 契约、输入校验、治理日志（确定性模型，不依赖外部 API）。
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmbeddingEndpointIntegrationTest {

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
    private static final String MODEL = "deterministic-embedding";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_embedding;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> ADMIN_KEY);
    }

    @Test
    void embeds_string_input_with_openai_shape() {
        ResponseEntity<String> response = post(body(MODEL, objectMapper.getNodeFactory()
                .textNode("你好")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = readJson(response);
        assertThat(json.path("object").asText()).isEqualTo("list");
        assertThat(json.path("model").asText()).isEqualTo(MODEL);
        assertThat(json.path("data").size()).isEqualTo(1);
        assertThat(json.path("data").get(0).path("object").asText()).isEqualTo("embedding");
        assertThat(json.path("data").get(0).path("embedding").size()).isEqualTo(384);
        assertThat(json.path("usage").path("prompt_tokens").asLong()).isEqualTo(2L);
        assertThat(json.path("usage").path("total_tokens").asLong()).isEqualTo(2L);
    }

    @Test
    void embeds_array_input_in_order() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        ArrayNode input = body.putArray("input");
        input.add("你好");
        input.add("世界");
        input.add("abc");

        JsonNode json = readJson(post(body));

        assertThat(json.path("data").size()).isEqualTo(3);
        for (int index = 0; index < 3; index++) {
            assertThat(json.path("data").get(index).path("index").asInt()).isEqualTo(index);
        }
        assertThat(json.path("usage").path("prompt_tokens").asLong()).isGreaterThan(0L);
    }

    @Test
    void unknown_model_returns_400_chinese_error() {
        ResponseEntity<String> response = post(body("missing-embedding", objectMapper
                .getNodeFactory().textNode("你好")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = readJson(response).path("error");
        assertThat(error.path("message").asText()).contains("不存在");
        assertThat(error.path("type").asText()).isEqualTo("invalid_request_error");
        assertThat(error.path("param").asText()).isEqualTo("model");
        assertThat(error.path("code").asText()).isEqualTo("400");
    }

    @Test
    void unsupported_dimensions_returns_400_for_deterministic_model() {
        ObjectNode body = body(MODEL, objectMapper.getNodeFactory().textNode("你好"));
        body.put("dimensions", 128);

        ResponseEntity<String> response = post(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(response).path("error").path("param").asText())
                .isEqualTo("dimensions");
    }

    @Test
    void invalid_input_type_returns_400() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        body.putArray("input").add(123);

        ResponseEntity<String> response = post(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(response).path("error").path("param").asText()).isEqualTo("input");
    }

    @Test
    void embedding_call_is_logged_with_call_type() {
        ResponseEntity<String> response = post(body(MODEL, objectMapper.getNodeFactory()
                .textNode("知识库")));
        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));

        assertThat(log.getCallType()).isEqualTo("EMBEDDING");
        assertThat(log.getTargetName()).isEqualTo("embeddings");
        assertThat(log.getModelAlias()).isEqualTo(MODEL);
        assertThat(log.getPromptTokens()).isEqualTo(3);
        assertThat(log.getCost()).isEqualByComparingTo("0");
        assertThat(log.getStatus()).isEqualTo(200);
    }

    private ObjectNode body(String model, JsonNode input) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("input", input);
        return body;
    }

    private ResponseEntity<String> post(ObjectNode body) {
        return restClient.post()
                .uri("/v1/embeddings")
                .header("Authorization", "Bearer " + dataKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .toEntity(String.class);
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + response.getBody(), ex);
        }
    }

    private CallLogDO awaitLog(String requestId) {
        for (int index = 0; index < 50; index++) {
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
}
