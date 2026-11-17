package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.core.rag.RagDocumentCore;
import com.qtua.aihub.gateway.GatewayApplication;
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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-5.3 回退验收：关闭混合检索后与三期行为一致（向量 + 规则重排 + min_score）。
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagHybridDisabledIntegrationTest {

    private static final String TENANT = "test-tenant";
    private static final String QUERY = "年假 报销";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> keys = new HashMap<>();

    @LocalServerPort
    private int port;

    @Autowired
    private KeyService keyService;

    @Autowired
    private RagDocumentCore ragDocumentCore;

    private RestClient restClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_rag_hybrid_off;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> "hybrid-off-admin");
        registry.add("gateway.rag.search.hybrid-enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                })
                .build();
    }

    @Test
    void switch_off_keeps_phase_three_rerank_and_fields() {
        String project = "p-hybrid-off-rerank";
        upload(project, "target.md", QUERY);
        upload(project, "other.md", "无关内容");

        JsonNode response = search(project, QUERY, 3, 0.9, true);

        assertThat(response.path("reranked").asBoolean()).isTrue();
        assertThat(response.path("count").asInt()).isEqualTo(1);
        JsonNode hit = response.path("results").get(0);
        assertThat(hit.path("score").asDouble()).isGreaterThan(0.99);
        assertThat(hit.path("rerank_score").isNull()).isFalse();
        assertThat(hit.path("match_type").asText()).isEqualTo("vector");
        assertThat(hit.path("rrf_score").isNull()).isTrue();
        assertThat(hit.path("keyword_score").isNull()).isTrue();
    }

    @Test
    void switch_off_keeps_min_score_and_top_k_semantics() {
        String project = "p-hybrid-off-limit";
        upload(project, "target.md", QUERY);
        upload(project, "doc1.md", "年假制度说明");
        upload(project, "doc2.md", "报销流程说明");
        upload(project, "doc3.md", "其他制度说明");

        JsonNode threshold = search(project, QUERY, 3, 0.9, false);
        assertThat(threshold.path("count").asInt()).isEqualTo(1);

        JsonNode all = search(project, QUERY, 10, -1.0, false);
        assertThat(all.path("count").asInt()).isEqualTo(3);
    }

    private void upload(String project, String fileName, String content) {
        ragDocumentCore.ingest(TENANT, project, fileName, "text/plain",
                content.getBytes(StandardCharsets.UTF_8), false);
    }

    private JsonNode search(String project, String query, int topK, double minScore,
                            boolean rerank) {
        String body = "{\"query\":\"" + query + "\",\"project_id\":\"" + project
                + "\",\"top_k\":" + topK + ",\"min_score\":" + minScore
                + ",\"rerank\":" + rerank + "}";
        ResponseEntity<String> response = restClient.post()
                .uri("/v1/rag/search")
                .header("Authorization", "Bearer " + keyFor(project))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("invalid JSON: " + response.getBody(), ex);
        }
    }

    private String keyFor(String project) {
        return keys.computeIfAbsent(project,
                ignored -> TestKeys.create(keyService, TENANT, project));
    }
}
