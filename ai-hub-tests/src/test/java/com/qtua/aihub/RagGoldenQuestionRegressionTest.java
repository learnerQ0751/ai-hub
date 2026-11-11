package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.4 黄金问题集回归入口（离线）。
 *
 * <p>读取 `rag/golden-questions.yml`：上传语料 → 逐题调用 `/v1/rag/answer` → 校验“应命中”的 citations
 * 指向期望文档、“应无命中”的返回兜底且不调模型。使用 H2 + 确定性 embedding + MockWebServer，
 * 可作为轻量评测入口按需扩展；不建设评测平台。</p>
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagGoldenQuestionRegressionTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String MODEL = "chat";
    private static final String ANSWER = "根据资料回答 [1]";
    private static final String DATASET = "rag/golden-questions.yml";
    private static final MockWebServer UPSTREAM = startServer();

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_rag_golden;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
    }

    @AfterAll
    static void stopServer() throws IOException {
        UPSTREAM.shutdown();
    }

    @Test
    void golden_questions_pass() {
        Map<String, Object> dataset = loadDataset();
        uploadDocuments(asList(dataset.get("documents")));
        int expectedModelCalls = 0;
        List<String> failures = new ArrayList<>();
        for (Map<String, Object> question : asList(dataset.get("questions"))) {
            String id = asString(question, "id");
            boolean expectNoHit = asBoolean(question, "expect_no_hit");
            // 每题都预置响应：即使 no_hit 断言失败也不会因上游无响应而挂起。
            UPSTREAM.enqueue(mockResponse());
            if (!expectNoHit) {
                expectedModelCalls++;
            }
            JsonNode response = answer(question);
            if (expectNoHit) {
                collectNoHitFailures(id, response, failures);
            } else {
                collectHitFailures(id, question, response, failures);
            }
        }
        assertThat(failures).as("golden question failures").isEmpty();
        assertThat(UPSTREAM.getRequestCount()).isEqualTo(expectedModelCalls);
    }

    private void collectNoHitFailures(String id, JsonNode response, List<String> failures) {
        if (!response.path("no_hit").asBoolean()) {
            failures.add(id + ": expected no_hit=true but got false");
        }
        if (response.path("citations").size() > 0) {
            failures.add(id + ": expected empty citations but got "
                    + response.path("citations").size());
        }
        if (response.path("answer").asText().contains("[1]")) {
            failures.add(id + ": fallback answer must not contain fabricated citations");
        }
    }

    private void collectHitFailures(String id, Map<String, Object> question, JsonNode response,
                                    List<String> failures) {
        if (response.path("no_hit").asBoolean()) {
            failures.add(id + ": expected hit but got no_hit=true");
            return;
        }
        if (response.path("citations").size() == 0) {
            failures.add(id + ": expected citations but got none");
            return;
        }
        String expectedSource = asString(question, "expected_source");
        String actualSource = response.path("citations").get(0).path("source").asText();
        if (!expectedSource.equals(actualSource)) {
            failures.add(id + ": expected source " + expectedSource + " but got " + actualSource);
        }
        if (response.path("answer").asText().isBlank()) {
            failures.add(id + ": expected non-empty answer");
        }
    }

    private void uploadDocuments(List<Map<String, Object>> documents) {
        for (Map<String, Object> document : documents) {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            byte[] bytes = asString(document, "content").getBytes(StandardCharsets.UTF_8);
            String source = asString(document, "source");
            parts.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return source;
                }
            });
            parts.add("project_id", asString(document, "project_id"));
            ResponseEntity<String> response = restClient.post()
                    .uri("/v1/rag/documents")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .body(parts)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        }
    }

    private JsonNode answer(Map<String, Object> question) {
        String body = "{\"query\":\"" + asString(question, "question")
                + "\",\"project_id\":\"" + asString(question, "project_id")
                + "\",\"model\":\"" + MODEL
                + "\",\"top_k\":3,\"min_score\":" + asDouble(question, "min_score")
                + ",\"allow_free_answer\":false}";
        ResponseEntity<String> response = restClient.post()
                .uri("/v1/rag/answer")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(response);
    }

    private Map<String, Object> loadDataset() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(DATASET)) {
            assertThat(input).as("dataset " + DATASET).isNotNull();
            return new Yaml().load(input);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private String asString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    private boolean asBoolean(Map<String, Object> map, String key) {
        return Boolean.parseBoolean(asString(map, key));
    }

    private double asDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + response.getBody(), ex);
        }
    }

    private static MockResponse mockResponse() {
        String body = "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"upstream-chat\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + ANSWER + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
