package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.qtua.aihub.api.KeyService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.3 端到端验收：命中已知切片、项目隔离、阈值/条数、元数据过滤、无命中与治理日志。
 *
 * <p>使用 H2 + 确定性 embedding + 内存向量存储。离线回归中让查询文本与目标切片文本一致，
 * 保证确定性 embedding 下余弦为 1；真实语义命中由 REQ-3.4 的真实链路演示覆盖。</p>
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagSearchIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private KeyService keyService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.qtua.aihub.core.rag.RagDocumentCore ragDocumentCore;

    private final java.util.Map<String, String> keys = new java.util.HashMap<>();

    /**
     * 按项目取虚拟 Key（Key 绑定租户+项目）。
     *
     * @param projectId 项目
     * @return 明文 Key
     */
    private String keyFor(String projectId) {
        return keys.computeIfAbsent(projectId,
                ignored -> TestKeys.create(keyService, "test-tenant", projectId));
    }

    private static final String ADMIN_KEY = "integration-master";
    private static final String QUERY = "年假 报销";

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_rag_search;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> ADMIN_KEY);
    }

    @Test
    void search_hits_known_chunk_with_full_fields() {
        String project = "p-search-hit";
        String docId = upload(project, "demo.md", QUERY);
        upload(project, "other.md", "与问题无关的其他内容");

        JsonNode response = search(searchBody(project, QUERY, 3, 0.9, null, false));

        assertThat(response.path("no_hit").asBoolean()).isFalse();
        assertThat(response.path("count").asInt()).isEqualTo(1);
        assertThat(response.has("embeddingTokens")).isFalse();
        assertThat(response.has("embeddingModel")).isFalse();
        JsonNode hit = response.path("results").get(0);
        assertThat(hit.path("content").asText()).isEqualTo(QUERY);
        assertThat(hit.path("source").asText()).isEqualTo("demo.md");
        assertThat(hit.path("doc_id").asText()).isEqualTo(docId);
        assertThat(hit.path("chunk_id").asText()).startsWith(docId);
        assertThat(hit.path("chunk_index").asInt()).isZero();
        assertThat(hit.path("score").asDouble()).isGreaterThan(0.99);
        assertThat(hit.path("rerank_score").isNull()).isTrue();
        assertThat(response.path("reranked").asBoolean()).isFalse();
    }

    @Test
    void project_isolation_never_returns_other_project_chunks() {
        String docA = upload("p-isolation-a", "a.md", QUERY);
        String docB = upload("p-isolation-b", "b.md", QUERY);

        JsonNode resultA = search(searchBody("p-isolation-a", QUERY, 3, -1.0, null, false));
        JsonNode resultB = search(searchBody("p-isolation-b", QUERY, 3, -1.0, null, false));

        for (JsonNode hit : resultA.path("results")) {
            assertThat(hit.path("doc_id").asText()).isEqualTo(docA);
        }
        for (JsonNode hit : resultB.path("results")) {
            assertThat(hit.path("doc_id").asText()).isEqualTo(docB);
        }
    }

    @Test
    void min_score_and_top_k_are_applied() {
        String project = "p-search-limit";
        upload(project, "target.md", QUERY);
        upload(project, "doc1.md", "年假制度说明");
        upload(project, "doc2.md", "报销流程说明");
        upload(project, "doc3.md", "其他制度说明");

        JsonNode threshold = search(searchBody(project, QUERY, 3, 0.9, null, false));
        assertThat(threshold.path("count").asInt()).isEqualTo(1);

        JsonNode all = search(searchBody(project, QUERY, 10, -1.0, null, false));
        assertThat(all.path("count").asInt()).isEqualTo(3);
    }

    @Test
    void filters_by_source_and_doc_ids() {
        String project = "p-search-filter";
        upload(project, "a.md", QUERY);
        String docB = upload(project, "b.md", "另一份文档内容");

        JsonNode bySource = search(searchBody(project, QUERY, 3, -1.0, filter(null, "b.md"),
                false));
        assertThat(bySource.path("results").size()).isGreaterThan(0);
        for (JsonNode hit : bySource.path("results")) {
            assertThat(hit.path("source").asText()).isEqualTo("b.md");
            assertThat(hit.path("doc_id").asText()).isEqualTo(docB);
        }

        JsonNode byDocId = search(searchBody(project, QUERY, 3, -1.0,
                filter(new String[]{docB}, null), false));
        assertThat(byDocId.path("results").size()).isGreaterThan(0);
        for (JsonNode hit : byDocId.path("results")) {
            assertThat(hit.path("doc_id").asText()).isEqualTo(docB);
        }

        JsonNode missing = search(searchBody(project, QUERY, 3, -1.0,
                filter(null, "missing.md"), false));
        assertThat(missing.path("no_hit").asBoolean()).isTrue();
        assertThat(missing.path("count").asInt()).isZero();
    }

    @Test
    void no_hit_returns_200_with_empty_list() {
        ResponseEntity<String> response = searchEntity(
                searchBody("p-search-empty", "不存在的问题", 3, 0.0, null, false));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJson(response);
        assertThat(body.path("no_hit").asBoolean()).isTrue();
        assertThat(body.path("count").asInt()).isZero();
        assertThat(body.path("results").size()).isZero();
    }

    @Test
    void rerank_flag_is_honored() {
        String project = "p-search-rerank";
        upload(project, "target.md", QUERY);
        upload(project, "other.md", "无关内容");

        JsonNode enabled = search(searchBody(project, QUERY, 3, 0.9, null, true));
        JsonNode disabled = search(searchBody(project, QUERY, 3, 0.9, null, false));

        assertThat(enabled.path("reranked").asBoolean()).isTrue();
        assertThat(disabled.path("reranked").asBoolean()).isFalse();
    }

    @Test
    void invalid_parameters_return_chinese_errors() {
        ResponseEntity<String> missingQuery = searchEntity(
                "{\"project_id\":\"p-search-error\"}");
        assertThat(missingQuery.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = readJson(missingQuery).path("error");
        assertThat(error.path("message").asText()).contains("query");
        assertThat(error.path("type").asText()).isEqualTo("invalid_request_error");
        assertThat(error.path("param").asText()).isEqualTo("query");
        assertThat(error.path("code").asText()).isEqualTo("400");

        ResponseEntity<String> badScore = searchEntity(
                searchBody("p-search-error", "年假", 3, 2.0, null, false));
        assertThat(badScore.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(badScore).path("error").path("param").asText())
                .isEqualTo("min_score");
    }

    @Test
    void search_call_is_logged_with_rag_target() {
        String project = "p-search-log";
        upload(project, "log.md", QUERY);

        ResponseEntity<String> response = searchEntity(
                searchBody(project, QUERY, 3, 0.9, null, false));
        CallLogDO log = awaitLog(response.getHeaders().getFirst("X-Request-Id"));

        assertThat(log.getCallType()).isEqualTo("RAG");
        assertThat(log.getTargetName()).isEqualTo("search_knowledge");
        assertThat(log.getModelAlias()).isEqualTo("search_knowledge");
        assertThat(log.getCost()).isEqualByComparingTo("0");
        assertThat(log.getStatus()).isEqualTo(200);
    }

    private String upload(String projectId, String fileName, String content) {
        return ragDocumentCore.ingest("test-tenant", projectId, fileName, "text/plain",
                content.getBytes(StandardCharsets.UTF_8), false).document().docId();
    }

    private JsonNode search(String body) {
        ResponseEntity<String> response = searchEntity(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(response);
    }

    private String keyForBody(String body) {
        try {
            return keyFor(objectMapper.readTree(body).path("project_id").asText());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("invalid request body", ex);
        }
    }

    private ResponseEntity<String> searchEntity(String body) {
        return restClient.post()
                .uri("/v1/rag/search")
                .header("Authorization", "Bearer " + keyForBody(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private String searchBody(String projectId, String query, int topK, double minScore,
                              ObjectNode filters, boolean rerank) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query);
        body.put("project_id", projectId);
        body.put("top_k", topK);
        body.put("min_score", minScore);
        body.put("rerank", rerank);
        if (filters != null) {
            body.set("filters", filters);
        }
        return body.toString();
    }

    private ObjectNode filter(String[] docIds, String source) {
        ObjectNode filters = objectMapper.createObjectNode();
        if (docIds != null) {
            ArrayNode array = filters.putArray("doc_ids");
            for (String docId : docIds) {
                array.add(docId);
            }
        }
        if (source != null) {
            filters.put("source", source);
        }
        return filters;
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
