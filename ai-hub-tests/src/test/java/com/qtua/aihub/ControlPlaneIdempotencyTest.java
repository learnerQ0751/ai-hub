package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.common.util.KeyHasher;
import com.qtua.aihub.console.ConsoleApplication;
import com.qtua.aihub.manager.audit.AuditLogManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-5.2 控制面幂等验收：重复调用不产生重复数据、不重复审计；失败重试无半写；
 * 并发写由唯一约束收敛。
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ControlPlaneIdempotencyTest {

    private static final String ADMIN_KEY = "idem-admin-key";
    private static final String TENANT = "idem-tenant";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private KeyService keyService;

    @Autowired
    private AuditLogManager auditLogManager;

    private RestClient restClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> ADMIN_KEY);
        registry.add("gateway.secret-key", () -> "idem-secret");
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
    void key_block_and_unblock_are_idempotent_and_audited_once() {
        String key = TestKeys.create(keyService, TENANT, "p-key");
        String hash = KeyHasher.sha256Hex(key);

        assertThat(post("/key/block", "{\"key\":\"" + key + "\"}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/key/block", "{\"key\":\"" + key + "\"}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/key/unblock", "{\"key\":\"" + key + "\"}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/key/unblock", "{\"key\":\"" + key + "\"}").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(auditCount(TENANT, "KEY_BLOCK", hash)).isEqualTo(1);
        assertThat(auditCount(TENANT, "KEY_UNBLOCK", hash)).isEqualTo(1);
        assertThat(keyService.info(key).info().blocked()).isFalse();
    }

    @Test
    void model_create_is_idempotent_for_identical_payload() {
        String alias = "idem-alias-" + suffix();
        String body = modelBody(alias, "desc", "http://localhost:1", "m1", 1);

        ResponseEntity<String> first = post("/v1/admin/models", body);
        ResponseEntity<String> second = post("/v1/admin/models", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(second).path("alias").asText()).isEqualTo(alias);
        assertThat(readJson(second).path("deployments")).hasSize(1);

        assertThat(auditCount("default", "ALIAS_CREATE", alias)).isEqualTo(1);
        ResponseEntity<String> list = get("/v1/admin/models");
        assertThat(countOf(list.getBody(), "\"alias\":\"" + alias + "\"")).isEqualTo(1);

        ResponseEntity<String> different = post("/v1/admin/models",
                modelBody(alias, "other", "http://localhost:1", "m1", 1));
        // 同名不同配置是资源冲突，不是参数错误（D-03）。
        assertThat(different.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(different).path("error").path("type").asText())
                .isEqualTo("conflict_error");
    }

    @Test
    void invalid_deployment_prevents_partial_alias_write() {
        String alias = "idem-half-" + suffix();
        String body = "{\"alias\":\"" + alias + "\",\"fallbacks\":[],\"deployments\":["
                + "{\"deployment_key\":\"" + alias + "#0\",\"api_base\":\"http://localhost:1\","
                + "\"api_key\":\"k\",\"upstream_model\":\"m1\"},"
                + "{\"deployment_key\":\"" + alias + "#1\",\"api_base\":\"http://localhost:1\"}"
                + "]}";

        ResponseEntity<String> response = post("/v1/admin/models", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/v1/admin/models/" + alias).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deployment_upsert_is_idempotent_and_audited_once() {
        String alias = "idem-deploy-" + suffix();
        assertThat(post("/v1/admin/models", "{\"alias\":\"" + alias
                + "\",\"fallbacks\":[],\"deployments\":[]}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String body = "{\"deployment_key\":\"" + alias + "#0\","
                + "\"api_base\":\"http://localhost:1\",\"api_key\":\"up\","
                + "\"upstream_model\":\"m1\",\"weight\":2,\"priority\":0}";

        assertThat(post("/v1/admin/models/" + alias + "/deployments", body).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<String> second = post("/v1/admin/models/" + alias + "/deployments", body);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(second).path("deployments")).hasSize(1);
        assertThat(auditCount("default", "ALIAS_DEPLOYMENT_UPSERT", alias)).isEqualTo(1);
    }

    @Test
    void project_authorization_is_idempotent_even_under_concurrency() throws Exception {
        String suffix = suffix();
        String body = "{\"tenant_id\":\"" + TENANT + "\",\"source_project_id\":\"p-a-" + suffix
                + "\",\"target_project_id\":\"p-b-" + suffix
                + "\",\"resource_type\":\"MEMORY\"}";

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                tasks.add(() -> post("/v1/admin/project-authorizations", body)
                        .getStatusCode().value());
            }
            List<Future<Integer>> results = pool.invokeAll(tasks);
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(HttpStatus.OK.value());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(auditCount(TENANT, "PROJECT_AUTHORIZE", "p-a-" + suffix + "->p-b-" + suffix))
                .isEqualTo(1);
    }

    @Test
    void mcp_register_is_idempotent_for_identical_payload() {
        String name = "idem_mcp_" + suffix().replace("-", "");
        String body = "{\"name\":\"" + name + "\",\"transport\":\"http\","
                + "\"endpoint\":\"http://localhost:1/mcp\"}";

        ResponseEntity<String> first = post("/v1/admin/mcp/servers", body);
        ResponseEntity<String> second = post("/v1/admin/mcp/servers", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(readJson(second).path("serverId").asText())
                .isEqualTo(readJson(first).path("serverId").asText());
        assertThat(auditCount("default", "MCP_REGISTER", name)).isEqualTo(1);

        ResponseEntity<String> different = post("/v1/admin/mcp/servers",
                "{\"name\":\"" + name + "\",\"transport\":\"http\","
                        + "\"endpoint\":\"http://localhost:2/mcp\"}");
        assertThat(different.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void prompt_create_and_publish_are_idempotent() {
        String name = "idem_prompt_" + suffix().replace("-", "");
        String body = "{\"name\":\"" + name + "\",\"content\":\"User: hi {{name}}\","
                + "\"variables\":[\"name\"]}";

        ResponseEntity<String> first = post("/v1/admin/prompts", body);
        ResponseEntity<String> second = post("/v1/admin/prompts", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(readJson(second).path("version").asInt()).isEqualTo(1);
        assertThat(readJson(get("/v1/admin/prompts/" + name + "/versions")).path("versions"))
                .hasSize(1);
        assertThat(auditCount("default", "PROMPT_CREATE", name)).isEqualTo(1);

        ResponseEntity<String> different = post("/v1/admin/prompts",
                "{\"name\":\"" + name + "\",\"content\":\"User: other\",\"variables\":[]}");
        assertThat(different.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/v1/admin/prompts/" + name + "/publish", "{\"version\":1}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/v1/admin/prompts/" + name + "/publish", "{\"version\":1}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(auditCount("default", "PROMPT_PUBLISH", name)).isEqualTo(1);
    }

    @Test
    void rag_ingest_is_idempotent_by_content_hash() {
        String project = "p-rag-" + suffix();
        ResponseEntity<String> first = upload(project, "idem.txt", "同一份内容");
        ResponseEntity<String> second = upload(project, "idem.txt", "同一份内容");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(second).path("doc_id").asText())
                .isEqualTo(readJson(first).path("doc_id").asText());
        assertThat(readJson(get("/v1/admin/rag/documents?project_id=" + project)).path("documents"))
                .hasSize(1);
        assertThat(auditCount("default", "RAG_INGEST",
                readJson(first).path("doc_id").asText())).isEqualTo(1);
    }

    @Test
    void idempotency_key_replays_key_generation_across_calls() {
        String tenant = "idem-gen-" + suffix();
        String header = "gen-" + UUID.randomUUID();
        String body = "{\"models\":[],\"tenant_id\":\"" + tenant
                + "\",\"project_id\":\"p-gen\"}";

        ResponseEntity<String> first = postWithIdempotency("/key/generate", body, header);
        ResponseEntity<String> second = postWithIdempotency("/key/generate", body, header);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(second).path("key").asText())
                .isEqualTo(readJson(first).path("key").asText());
        assertThat(second.getHeaders().getFirst("X-Idempotent-Replay")).isEqualTo("true");
        assertThat(readJson(get("/v1/admin/keys?tenant_id=" + tenant)).path("count").asInt())
                .isEqualTo(1);
        assertThat(auditCount(tenant, "KEY_GENERATE", KeyHasher.sha256Hex(
                readJson(first).path("key").asText()))).isEqualTo(1);
    }

    @Test
    void idempotency_key_rejects_different_request() {
        String header = "conflict-" + UUID.randomUUID();
        assertThat(postWithIdempotency("/key/generate",
                "{\"models\":[],\"tenant_id\":\"" + TENANT + "\",\"project_id\":\"p-1\"}",
                header).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> conflict = postWithIdempotency("/key/generate",
                "{\"models\":[],\"tenant_id\":\"" + TENANT + "\",\"project_id\":\"p-2\"}",
                header);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(conflict).path("error").path("type").asText())
                .isEqualTo("idempotency_error");
    }

    @Test
    void idempotency_key_replays_prompt_version_creation() {
        String name = "idem_ver_" + suffix().replace("-", "");
        assertThat(post("/v1/admin/prompts", "{\"name\":\"" + name
                + "\",\"content\":\"User: v1\",\"variables\":[]}").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        String header = "ver-" + UUID.randomUUID();
        String body = "{\"content\":\"User: v2\",\"variables\":[]}";

        ResponseEntity<String> first = postWithIdempotency("/v1/admin/prompts/" + name + "/versions",
                body, header);
        ResponseEntity<String> second = postWithIdempotency("/v1/admin/prompts/" + name + "/versions",
                body, header);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(readJson(second).path("version").asInt())
                .isEqualTo(readJson(first).path("version").asInt());
        assertThat(readJson(get("/v1/admin/prompts/" + name + "/versions")).path("versions"))
                .hasSize(2);
    }

    private ResponseEntity<String> upload(String projectId, String fileName, String content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        parts.add("project_id", projectId);
        return restClient.post()
                .uri("/v1/admin/rag/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .body(parts)
                .retrieve()
                .toEntity(String.class);
    }

    private String modelBody(String alias, String description, String apiBase,
                             String upstreamModel, int weight) {
        return "{\"alias\":\"" + alias + "\",\"description\":\"" + description
                + "\",\"fallbacks\":[],\"deployments\":[{\"deployment_key\":\"" + alias
                + "#0\",\"api_base\":\"" + apiBase + "\",\"api_key\":\"up\","
                + "\"upstream_model\":\"" + upstreamModel + "\",\"weight\":" + weight
                + ",\"priority\":0}]}";
    }

    private int auditCount(String tenantId, String action, String target) {
        return auditLogManager.findPage(tenantId, action, target, null, null, 100, 0).size();
    }

    private int countOf(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private ResponseEntity<String> post(String path, String body) {
        return restClient.post()
                .uri(path)
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> postWithIdempotency(String path, String body, String key) {
        return restClient.post()
                .uri(path)
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> get(String path) {
        return restClient.get()
                .uri(path)
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .retrieve()
                .toEntity(String.class);
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("invalid JSON: " + response.getBody(), ex);
        }
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
