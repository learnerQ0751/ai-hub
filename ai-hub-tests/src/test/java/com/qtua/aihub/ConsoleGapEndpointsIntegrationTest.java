package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.trace.TraceEvent;
import com.qtua.aihub.console.ConsoleApplication;
import com.qtua.aihub.manager.trace.TraceEventManager;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第五期 §7.1 端点缺口验收：项目列表、文档切片列表、单 Key 详情、Trace 列表。
 *
 * <p>全部走控制面 master key；虚拟 Key 一律 403。</p>
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConsoleGapEndpointsIntegrationTest {

    private static final String MASTER_KEY = "integration-master";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TraceEventManager traceEventManager;

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
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
    }

    @Test
    void key_detail_returns_management_fields_and_404_for_unknown_hash() throws Exception {
        String suffix = suffix();
        String tenant = "tenant-key-detail-" + suffix;
        String project = "project-key-detail-" + suffix;
        String alias = "alias-key-detail-" + suffix;
        String plainKey = generateKey(tenant, project, alias);
        String hash = com.qtua.aihub.common.util.KeyHasher.sha256Hex(plainKey);

        ResponseEntity<String> detail = get("/v1/admin/keys/" + hash, MASTER_KEY);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJson(detail);
        assertThat(body.path("key_hash").asText()).isEqualTo(hash);
        assertThat(body.path("alias").asText()).isEqualTo(alias);
        assertThat(body.path("tenant_id").asText()).isEqualTo(tenant);
        assertThat(body.path("project_id").asText()).isEqualTo(project);
        assertThat(body.path("rpm_limit").asInt()).isEqualTo(10);
        assertThat(body.path("tpm_limit").asInt()).isEqualTo(1000);
        assertThat(body.path("metadata").path("team").asText()).isEqualTo("console");
        assertThat(body.path("blocked").asBoolean()).isFalse();
        assertThat(detail.getBody()).doesNotContain(plainKey);

        String missingHash = "0".repeat(64);
        ResponseEntity<String> missing = get("/v1/admin/keys/" + missingHash, MASTER_KEY);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readJson(missing).path("error").path("type").asText())
                .isEqualTo("not_found_error");

        ResponseEntity<String> virtualKey = get("/v1/admin/keys/" + hash, plainKey);
        assertThat(virtualKey.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void project_list_pages_and_filters_observed_tenants() throws Exception {
        String suffix = suffix();
        String tenant = "tenant-projects-" + suffix;
        String projectA = "project-a-" + suffix;
        String projectB = "project-b-" + suffix;
        String projectC = "project-c-" + suffix;
        generateKey(tenant, projectA, null);
        generateKey(tenant, projectB, null);
        generateKey(tenant, projectC, null);

        ResponseEntity<String> firstPage = get("/v1/admin/projects?tenant_id=" + tenant
                + "&limit=2", MASTER_KEY);
        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode first = readJson(firstPage);
        assertThat(first.path("count").asInt()).isEqualTo(2);
        assertThat(first.path("has_more").asBoolean()).isTrue();
        assertThat(first.path("items").get(0).path("tenant_id").asText()).isEqualTo(tenant);
        assertThat(first.path("items").get(0).path("name").asText()).isNotBlank();
        assertThat(first.path("items").get(0).path("status").asText()).isEqualTo("ACTIVE");

        ResponseEntity<String> secondPage = get("/v1/admin/projects?tenant_id=" + tenant
                + "&limit=2&offset=2", MASTER_KEY);
        JsonNode second = readJson(secondPage);
        assertThat(second.path("count").asInt()).isEqualTo(1);
        assertThat(second.path("has_more").asBoolean()).isFalse();

        ResponseEntity<String> disabled = get("/v1/admin/projects?tenant_id=" + tenant
                + "&status=DISABLED", MASTER_KEY);
        assertThat(readJson(disabled).path("count").asInt()).isZero();

        ResponseEntity<String> invalid = get("/v1/admin/projects?status=UNKNOWN", MASTER_KEY);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void document_chunk_list_returns_paged_chunks() throws Exception {
        String suffix = suffix();
        String project = "project-chunks-" + suffix;
        String content = "第一段：" + "甲".repeat(50) + "\n\n第二段：" + "乙".repeat(50)
                + "\n\n第三段：" + "丙".repeat(50);
        JsonNode uploaded = readJson(upload(project, "chunks.md",
                content.getBytes(StandardCharsets.UTF_8)));
        String docId = uploaded.path("doc_id").asText();
        assertThat(docId).isNotBlank();

        ResponseEntity<String> firstPage = get("/v1/admin/rag/documents/" + docId
                + "/chunks?project_id=" + project + "&limit=1", MASTER_KEY);
        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode first = readJson(firstPage);
        assertThat(first.path("count").asInt()).isEqualTo(1);
        assertThat(first.path("has_more").asBoolean()).isTrue();
        JsonNode chunk = first.path("items").get(0);
        assertThat(chunk.path("chunk_id").asText()).startsWith(docId);
        assertThat(chunk.path("doc_id").asText()).isEqualTo(docId);
        assertThat(chunk.path("project_id").asText()).isEqualTo(project);
        assertThat(chunk.path("chunk_index").asInt()).isZero();
        assertThat(chunk.path("content").asText()).isNotBlank();
        assertThat(chunk.path("char_count").asInt()).isPositive();
        assertThat(chunk.path("source").asText()).isEqualTo("chunks.md");
        assertThat(chunk.path("index_status").asText()).isNotBlank();
        assertThat(chunk.path("created_at").asText()).isNotBlank();

        ResponseEntity<String> secondPage = get("/v1/admin/rag/documents/" + docId
                + "/chunks?project_id=" + project + "&limit=1&offset=1", MASTER_KEY);
        JsonNode second = readJson(secondPage);
        assertThat(second.path("items").get(0).path("chunk_index").asInt()).isEqualTo(1);
        assertThat(second.path("has_more").asBoolean()).isFalse();

        ResponseEntity<String> missingProject = get("/v1/admin/rag/documents/" + docId + "/chunks",
                MASTER_KEY);
        assertThat(missingProject.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> unknownDoc = get("/v1/admin/rag/documents/doc-missing-" + suffix
                + "/chunks?project_id=" + project, MASTER_KEY);
        assertThat(unknownDoc.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> wrongProject = get("/v1/admin/rag/documents/" + docId
                + "/chunks?project_id=other-" + suffix, MASTER_KEY);
        assertThat(wrongProject.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void trace_list_aggregates_by_trace_and_filters() {
        String suffix = suffix();
        String tenant = "tenant-traces-" + suffix;
        String project = "project-traces-" + suffix;
        String keyHash = "key-" + suffix;
        Instant base = Instant.parse("2026-11-17T09:00:00Z");
        String traceOk = "trace-ok-" + suffix;
        String traceError = "trace-err-" + suffix;

        append(traceOk, tenant, project, keyHash, "gateway", 200, base);
        append(traceOk, tenant, project, keyHash, "model", 200, base.plusMillis(120));
        append(traceError, tenant, project, keyHash, "gateway", 500, base.plusSeconds(30));

        ResponseEntity<String> listed = get("/v1/admin/traces?tenant_id=" + tenant, MASTER_KEY);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJson(listed);
        assertThat(body.path("count").asInt()).isEqualTo(2);
        assertThat(body.path("has_more").asBoolean()).isFalse();
        JsonNode newest = body.path("items").get(0);
        assertThat(newest.path("trace_id").asText()).isEqualTo(traceError);
        assertThat(newest.path("event_count").asInt()).isEqualTo(1);
        assertThat(newest.path("status").asInt()).isEqualTo(500);
        JsonNode older = body.path("items").get(1);
        assertThat(older.path("trace_id").asText()).isEqualTo(traceOk);
        assertThat(older.path("event_count").asInt()).isEqualTo(2);
        assertThat(older.path("key_id").asText()).isEqualTo(keyHash);
        assertThat(older.path("project_id").asText()).isEqualTo(project);
        assertThat(older.path("started_at").asText()).isNotBlank();
        assertThat(older.path("finished_at").asText()).isNotBlank();

        JsonNode onlyFailed = readJson(get("/v1/admin/traces?tenant_id=" + tenant
                + "&status=500", MASTER_KEY));
        assertThat(onlyFailed.path("count").asInt()).isEqualTo(1);
        assertThat(onlyFailed.path("items").get(0).path("trace_id").asText()).isEqualTo(traceError);

        JsonNode paged = readJson(get("/v1/admin/traces?tenant_id=" + tenant + "&limit=1", MASTER_KEY));
        assertThat(paged.path("count").asInt()).isEqualTo(1);
        assertThat(paged.path("has_more").asBoolean()).isTrue();

        JsonNode otherTenant = readJson(get("/v1/admin/traces?tenant_id=tenant-other-" + suffix,
                MASTER_KEY));
        assertThat(otherTenant.path("count").asInt()).isZero();

        ResponseEntity<String> invalidFrom = get("/v1/admin/traces?tenant_id=" + tenant
                + "&from=not-a-time", MASTER_KEY);
        assertThat(invalidFrom.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void append(String traceId, String tenant, String project, String keyHash,
                        String segment, int status, Instant createdAt) {
        traceEventManager.append(new TraceEvent(traceId, tenant, project, keyHash, segment,
                "/v1/rag/answer", status, 10L, Map.of("probe", segment), createdAt));
    }

    private String generateKey(String tenant, String project, String alias) throws Exception {
        String aliasPart = alias == null ? "" : "\"alias\":\"" + alias + "\",";
        ResponseEntity<String> response = restClient.post().uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{" + aliasPart + "\"models\":[\"chat\"],"
                        + "\"metadata\":{\"team\":\"console\"},"
                        + "\"rpm_limit\":10,\"tpm_limit\":1000,"
                        + "\"tenant_id\":\"" + tenant + "\",\"project_id\":\"" + project + "\"}")
                .retrieve().toEntity(String.class);
        return readJson(response).path("key").asText();
    }

    private ResponseEntity<String> upload(String projectId, String fileName, byte[] content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        parts.add("project_id", projectId);
        return restClient.post().uri("/v1/admin/rag/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> get(String uri, String token) {
        return restClient.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve().toEntity(String.class);
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + response.getBody(), ex);
        }
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
