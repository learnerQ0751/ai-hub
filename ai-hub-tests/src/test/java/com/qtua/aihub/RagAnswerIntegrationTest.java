package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.4 端到端验收：带引用回答、无召回兜底、自由回答开关、空答案、中文错误与治理日志。
 *
 * <p>H2 + 确定性 embedding + 内存向量存储 + MockWebServer 上游；不依赖本机 PostgreSQL 与真实模型。</p>
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagAnswerIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String QUERY = "年假 报销";
    private static final String ANSWER = "年假为 5 天 [1]";
    private static final MockWebServer UPSTREAM = startServer();

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
        drainRequests();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_rag_answer;"
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
    void answer_with_citations_points_to_known_document() throws Exception {
        String project = "p-answer-hit";
        String docId = upload(project, "员工手册.md", QUERY);
        upload(project, "其他.md", "无关内容");
        UPSTREAM.enqueue(mockResponse(ANSWER));

        JsonNode response = answer(answerBody(project, QUERY, "chat", 3, 0.9, null, false));

        assertThat(response.path("answer").asText()).isEqualTo(ANSWER);
        assertThat(response.path("no_hit").asBoolean()).isFalse();
        assertThat(response.path("free_answer").asBoolean()).isFalse();
        JsonNode citation = response.path("citations").get(0);
        assertThat(citation.path("index").asInt()).isEqualTo(1);
        assertThat(citation.path("doc_id").asText()).isEqualTo(docId);
        assertThat(citation.path("chunk_id").asText()).startsWith(docId);
        assertThat(citation.path("source").asText()).isEqualTo("员工手册.md");
        assertThat(citation.path("score").asDouble()).isGreaterThan(0.99);
        assertThat(response.path("usage").path("total_tokens").asInt()).isEqualTo(2);

        RecordedRequest upstream = UPSTREAM.takeRequest(2, TimeUnit.SECONDS);
        assertThat(upstream).isNotNull();
        JsonNode prompt = objectMapper.readTree(upstream.getBody().readUtf8());
        assertThat(prompt.path("messages").get(0).path("content").asText()).contains("参考资料");
        assertThat(prompt.path("messages").get(1).path("content").asText())
                .contains("[1]（来源：员工手册.md）")
                .contains("问题：" + QUERY);
    }

    @Test
    void no_hit_returns_fallback_without_calling_model() {
        int before = UPSTREAM.getRequestCount();

        JsonNode response = answer(answerBody("p-answer-empty", "火星基地建设", "chat", 3, 0.9,
                null, false));

        assertThat(response.path("no_hit").asBoolean()).isTrue();
        assertThat(response.path("free_answer").asBoolean()).isFalse();
        assertThat(response.path("answer").asText()).isEqualTo("未找到相关资料。");
        assertThat(response.path("citations").size()).isZero();
        assertThat(response.path("usage").path("total_tokens").asInt()).isZero();
        assertThat(UPSTREAM.getRequestCount()).isEqualTo(before);
    }

    @Test
    void no_hit_free_answer_calls_model_without_citations() throws Exception {
        UPSTREAM.enqueue(mockResponse("通用建议，仅供参考。"));

        JsonNode response = answer(answerBody("p-answer-free", "火星基地建设", "chat", 3, 0.9,
                null, true));

        assertThat(response.path("no_hit").asBoolean()).isTrue();
        assertThat(response.path("free_answer").asBoolean()).isTrue();
        assertThat(response.path("answer").asText()).isEqualTo("通用建议，仅供参考。");
        assertThat(response.path("citations").size()).isZero();
        RecordedRequest upstream = UPSTREAM.takeRequest(2, TimeUnit.SECONDS);
        assertThat(upstream).isNotNull();
        JsonNode prompt = objectMapper.readTree(upstream.getBody().readUtf8());
        assertThat(prompt.path("messages").get(0).path("content").asText()).contains("通用知识");
    }

    @Test
    void filters_are_forwarded_to_search() {
        String project = "p-answer-filter";
        upload(project, "员工手册.md", QUERY);
        ObjectNode filters = objectMapper.createObjectNode().put("source", "不存在.md");
        int before = UPSTREAM.getRequestCount();

        JsonNode response = answer(answerBody(project, QUERY, "chat", 3, 0.9, filters, false));

        assertThat(response.path("no_hit").asBoolean()).isTrue();
        assertThat(response.path("citations").size()).isZero();
        assertThat(UPSTREAM.getRequestCount()).isEqualTo(before);
    }

    @Test
    void blank_model_returns_400_with_chinese_message() {
        ResponseEntity<String> response = answerEntity(
                answerBody("p-answer-model", QUERY, " ", 3, 0.9, null, false));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = readJson(response).path("error");
        assertThat(error.path("message").asText()).contains("model");
        assertThat(error.path("type").asText()).isEqualTo("invalid_request_error");
        assertThat(error.path("param").asText()).isEqualTo("model");
        assertThat(error.path("code").asText()).isEqualTo("400");
    }

    @Test
    void empty_model_answer_uses_configured_fallback() {
        String project = "p-answer-empty-content";
        upload(project, "员工手册.md", QUERY);
        UPSTREAM.enqueue(mockResponse(""));

        JsonNode response = answer(answerBody(project, QUERY, "chat", 3, 0.9, null, false));

        assertThat(response.path("answer").asText()).isEqualTo("模型未返回有效答案。");
        assertThat(response.path("citations").size()).isEqualTo(1);
        assertThat(response.path("usage").path("prompt_tokens").asInt()).isEqualTo(1);
    }

    @Test
    void answer_call_is_logged_as_rag_answer() {
        String project = "p-answer-log";
        upload(project, "员工手册.md", QUERY);
        UPSTREAM.enqueue(mockResponse(ANSWER));

        ResponseEntity<String> response = answerEntity(
                answerBody(project, QUERY, "chat", 3, 0.9, null, false));
        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));

        assertThat(log.getCallType()).isEqualTo("RAG");
        assertThat(log.getTargetName()).isEqualTo("rag_answer");
        assertThat(log.getModelAlias()).isEqualTo("rag_answer");
        assertThat(log.getCost()).isEqualByComparingTo("0");
        assertThat(log.getStatus()).isEqualTo(200);
    }

    private String upload(String projectId, String fileName, String content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        parts.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        parts.add("project_id", projectId);
        ResponseEntity<String> response = restClient.post()
                .uri("/v1/rag/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> headers.setBearerAuth(MASTER_KEY))
                .body(parts)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        return readJson(response).path("doc_id").asText();
    }

    private JsonNode answer(String body) {
        ResponseEntity<String> response = answerEntity(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(response);
    }

    private ResponseEntity<String> answerEntity(String body) {
        return restClient.post()
                .uri("/v1/rag/answer")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private String answerBody(String projectId, String query, String model, int topK,
                              double minScore, ObjectNode filters, boolean allowFreeAnswer) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query);
        body.put("project_id", projectId);
        body.put("model", model);
        body.put("top_k", topK);
        body.put("min_score", minScore);
        body.put("allow_free_answer", allowFreeAnswer);
        if (filters != null) {
            body.set("filters", filters);
        }
        return body.toString();
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

    private void drainRequests() {
        try {
            while (UPSTREAM.takeRequest(5, TimeUnit.MILLISECONDS) != null) {
                // 清空上一用例残留的上游请求，保证本用例的 takeRequest 取到自己的请求。
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static MockResponse mockResponse(String content) {
        String body = "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"upstream-chat\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}],"
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
