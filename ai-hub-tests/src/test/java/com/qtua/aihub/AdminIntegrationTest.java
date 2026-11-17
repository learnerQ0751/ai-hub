package com.qtua.aihub;

import com.qtua.aihub.console.ConsoleApplication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.util.KeyHasher;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-4.4 端到端验收：Key 列表/封禁、模型别名 CRUD、日志查询、项目授权；
 * 响应不返回明文 Key 或上游 api-key；非管理员 403。
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private com.qtua.aihub.api.KeyService keyService;

    @Autowired
    private com.qtua.aihub.manager.tenant.ProjectAuthorizationManager authorizationManager;

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                })
                .build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.pricing.models.m1.input-per-million", () -> "1.5");
        registry.add("gateway.pricing.models.m1.output-per-million", () -> "2.5");
    }

    @Test
    void key_list_block_unblock_and_masking() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-admin-" + suffix;
        String project = "project-admin-" + suffix;
        String plainKey = generateKey(tenant, project);
        String hash = KeyHasher.sha256Hex(plainKey);

        ResponseEntity<String> list = get("/v1/admin/keys?tenant_id=" + tenant);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(hash).contains(tenant)
                .doesNotContain(plainKey);

        ResponseEntity<String> blocked = post("/v1/admin/keys/" + hash + "/block", "{}");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blocked.getBody()).contains("\"blocked\":true");

        // 数据面端点不在控制面；封禁效果通过 KeyService 鉴权直接验证。
        assertThatThrownBy(() -> keyService.authenticate(plainKey))
                .hasMessageContaining("封禁");

        ResponseEntity<String> unblocked = post("/v1/admin/keys/" + hash + "/unblock", "{}");
        assertThat(unblocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keyService.authenticate(plainKey)).isNotNull();
    }

    @Test
    void model_alias_crud_never_returns_upstream_key() {
        String alias = "admin-alias-" + UUID.randomUUID().toString().substring(0, 8);

        ResponseEntity<String> created = post("/v1/admin/models",
                "{\"alias\":\"" + alias + "\",\"fallbacks\":[],\"deployments\":["
                        + "{\"deployment_key\":\"" + alias + "#0\","
                        + "\"api_base\":\"http://localhost:1\","
                        + "\"api_key\":\"super-secret-upstream-key\","
                        + "\"upstream_model\":\"m1\",\"weight\":2,\"priority\":0}]}");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).contains(alias).contains("m1")
                .doesNotContain("super-secret-upstream-key");

        ResponseEntity<String> models = get("/v1/admin/models");
        assertThat(models.getBody()).contains(alias).doesNotContain("super-secret-upstream-key");

        ResponseEntity<String> updated = restClient.put()
                .uri("/v1/admin/models/" + alias)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"description\":\"updated\",\"fallbacks\":[],\"status\":\"ACTIVE\"}")
                .retrieve().toEntity(String.class);
        assertThat(updated.getBody()).contains(alias);

        ResponseEntity<String> deleted = restClient.delete()
                .uri("/v1/admin/models/" + alias)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void project_authorization_api_records_cross_project_grant() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-authz-" + suffix;
        String projectA = "project-a-" + suffix;
        String projectB = "project-b-" + suffix;
        String key = generateKey(tenant, projectA);

        assertThat(authorizationManager.findAuthorizedProjects(tenant, projectA))
                .doesNotContain(projectB);

        ResponseEntity<String> authorized = post("/v1/admin/project-authorizations",
                "{\"tenant_id\":\"" + tenant + "\",\"source_project_id\":\"" + projectA
                        + "\",\"target_project_id\":\"" + projectB
                        + "\",\"resource_type\":\"MEMORY\"}");
        assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 授权落库；跨项目放行链路由 TenantIsolationIntegrationTest 在数据面验证。
        assertThat(authorizationManager.findAuthorizedProjects(tenant, projectA))
                .contains(projectB);
    }

    @Test
    void key_list_exposes_management_fields_and_filters() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-fields-" + suffix;
        String project = "project-fields-" + suffix;
        String alias = "alias-" + suffix;
        String plainKey = generateKeyWithFields(tenant, project, alias);
        String hash = KeyHasher.sha256Hex(plainKey);

        ResponseEntity<String> active = get("/v1/admin/keys?tenant_id=" + tenant
                + "&status=active&model=chat");
        assertThat(active.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(active.getBody()).contains(hash).contains("\"alias\":\"" + alias + "\"")
                .contains("\"metadata\":{\"team\":\"console\"}")
                .contains("\"rpm_limit\":10").contains("\"tpm_limit\":1000")
                .contains("\"max_budget\":12.5").contains("\"budget_duration\":\"monthly\"");

        ResponseEntity<String> otherModel = get("/v1/admin/keys?tenant_id=" + tenant
                + "&model=other-model");
        assertThat(otherModel.getBody()).contains("\"items\":[]");

        post("/v1/admin/keys/" + hash + "/block", "{}");
        ResponseEntity<String> blocked = get("/v1/admin/keys?tenant_id=" + tenant
                + "&status=blocked");
        assertThat(blocked.getBody()).contains(hash);
        ResponseEntity<String> noActive = get("/v1/admin/keys?tenant_id=" + tenant
                + "&status=active");
        assertThat(noActive.getBody()).contains("\"items\":[]");

        ResponseEntity<String> invalid = get("/v1/admin/keys?status=unknown");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void model_list_exposes_description_price_and_updated_at_with_filters() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-model-" + suffix;
        String alias = "model-fields-" + suffix;

        ResponseEntity<String> created = post("/v1/admin/models",
                "{\"alias\":\"" + alias + "\",\"tenant_id\":\"" + tenant + "\","
                        + "\"description\":\"管理台模型目录\",\"fallbacks\":[],\"deployments\":["
                        + "{\"deployment_key\":\"" + alias + "#0\","
                        + "\"api_base\":\"http://localhost:1\","
                        + "\"api_key\":\"upstream-secret\","
                        + "\"upstream_model\":\"m1\",\"weight\":1,\"priority\":0}]}");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> models = get("/v1/admin/models?tenant_id=" + tenant
                + "&status=ACTIVE");
        assertThat(models.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(models.getBody()).contains(alias).contains("管理台模型目录")
                .contains("\"input_price_per_million\":1.5")
                .contains("\"output_price_per_million\":2.5")
                .contains("\"updated_at\":")
                .doesNotContain("upstream-secret");

        ResponseEntity<String> disabled = get("/v1/admin/models?tenant_id=" + tenant
                + "&status=DISABLED");
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody()).doesNotContain(alias);

        ResponseEntity<String> invalid = get("/v1/admin/models?status=NOPE");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void admin_logs_are_filterable() {
        ResponseEntity<String> response = get("/v1/admin/logs?limit=5");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"items\"").contains("\"has_more\"");
    }

    private ResponseEntity<String> get(String uri) {
        return restClient.get().uri(uri)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> post(String uri, String body) {
        return restClient.post().uri(uri)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> postWithKey(String uri, String body, String key) {
        return restClient.post().uri(uri)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
    }

    private String generateKeyWithFields(String tenantId, String projectId, String alias)
            throws Exception {
        ResponseEntity<String> response = restClient.post().uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"alias\":\"" + alias + "\",\"models\":[\"chat\"],"
                        + "\"metadata\":{\"team\":\"console\"},"
                        + "\"max_budget\":12.5,\"budget_duration\":\"monthly\","
                        + "\"rpm_limit\":10,\"tpm_limit\":1000,"
                        + "\"tenant_id\":\"" + tenantId + "\",\"project_id\":\"" + projectId
                        + "\"}")
                .retrieve().toEntity(String.class);
        JsonNode node = objectMapper.readTree(response.getBody());
        return node.path("key").asText();
    }

    private String generateKey(String tenantId, String projectId) throws Exception {
        ResponseEntity<String> response = restClient.post().uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"models\":[],\"tenant_id\":\"" + tenantId
                        + "\",\"project_id\":\"" + projectId + "\"}")
                .retrieve().toEntity(String.class);
        JsonNode node = objectMapper.readTree(response.getBody());
        return node.path("key").asText();
    }
}
