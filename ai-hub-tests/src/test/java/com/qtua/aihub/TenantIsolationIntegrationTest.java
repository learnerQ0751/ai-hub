package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.manager.tenant.ProjectAuthorizationManager;
import com.qtua.aihub.manager.model.PromptTemplateBO;
import com.qtua.aihub.manager.prompt.PromptManager;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-4.1 端到端验收：虚拟 Key 绑定租户/项目、服务端派生过滤、越权 403、
 * 事务授权后跨项目记忆可读、调用日志带租户。
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @Autowired
    private ProjectAuthorizationManager authorizationManager;

    @Autowired
    private PromptManager promptManager;

    @Autowired
    private CallLogMapper callLogMapper;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                    // 测试直接断言状态码与错误体，不抛出。
                })
                .build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
    }

    @Test
    void tenant_key_can_access_own_memory_but_not_other_tenant() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String keyT1 = generateKey("tenant-a-" + suffix, "project-a-" + suffix);
        String keyT2 = generateKey("tenant-b-" + suffix, "project-b-" + suffix);

        ResponseEntity<String> saved = restClient.post().uri("/v1/memory/save")
                .header("Authorization", "Bearer " + keyT1)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"user_id\":\"u1\",\"session_id\":\"s1\",\"content\":\"tenant-a-secret\"}")
                .retrieve().toEntity(String.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> ownRecall = restClient.post().uri("/v1/memory/recall")
                .header("Authorization", "Bearer " + keyT1)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"user_id\":\"u1\",\"session_id\":\"s1\"}")
                .retrieve().toEntity(String.class);
        assertThat(ownRecall.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownRecall.getBody()).contains("tenant-a-secret");

        ResponseEntity<String> crossTenant = restClient.post().uri("/v1/memory/recall")
                .header("Authorization", "Bearer " + keyT2)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"tenant_id\":\"tenant-a-" + suffix + "\",\"project_id\":\"project-a-"
                        + suffix + "\",\"user_id\":\"u1\"}")
                .retrieve().toEntity(String.class);
        assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(crossTenant.getBody()).contains("tenant_access_denied");
    }

    @Test
    void request_parameters_cannot_override_key_binding() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String keyT1 = generateKey("tenant-a-" + suffix, "project-a-" + suffix);

        ResponseEntity<String> crossProject = restClient.post().uri("/v1/memory/save")
                .header("Authorization", "Bearer " + keyT1)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"project_id\":\"project-b-" + suffix
                        + "\",\"user_id\":\"u1\",\"content\":\"x\"}")
                .retrieve().toEntity(String.class);
        assertThat(crossProject.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(crossProject.getBody()).contains("tenant_access_denied");
    }

    @Test
    void explicit_authorization_allows_cross_project_memory() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-a-" + suffix;
        String projectA = "project-a-" + suffix;
        String projectB = "project-b-" + suffix;
        String keyT1 = generateKey(tenant, projectA);
        authorizationManager.authorize(tenant, projectA, projectB, "MEMORY");

        ResponseEntity<String> saved = restClient.post().uri("/v1/memory/save")
                .header("Authorization", "Bearer " + keyT1)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"project_id\":\"" + projectB
                        + "\",\"user_id\":\"u2\",\"content\":\"shared\"}")
                .retrieve().toEntity(String.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> recall = restClient.post().uri("/v1/memory/recall")
                .header("Authorization", "Bearer " + keyT1)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"project_id\":\"" + projectB + "\",\"user_id\":\"u2\"}")
                .retrieve().toEntity(String.class);
        assertThat(recall.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recall.getBody()).contains("shared");
    }

    @Test
    void rag_and_prompt_are_isolated_by_tenant() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String tenantA = "tenant-a-" + suffix;
        String tenantB = "tenant-b-" + suffix;
        String projectA = "project-a-" + suffix;
        String keyT1 = generateKey(tenantA, projectA);
        String keyT2 = generateKey(tenantB, "project-b-" + suffix);
        publishPrompt(tenantA, "shared-prompt", "tenant-a-content-" + suffix);
        publishPrompt(tenantB, "shared-prompt", "tenant-b-content-" + suffix);

        ResponseEntity<String> upload = restClient.post().uri("/v1/rag/documents")
                .header("Authorization", "Bearer " + keyT1)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(uploadBody("a.txt", "tenant-a knowledge", projectA))
                .retrieve().toEntity(String.class);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> otherList = restClient.get()
                .uri("/v1/rag/documents?project_id=" + projectA)
                .header("Authorization", "Bearer " + keyT2)
                .retrieve().toEntity(String.class);
        assertThat(otherList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> promptT1 = restClient.post()
                .uri("/v1/prompts/shared-prompt/render")
                .header("Authorization", "Bearer " + keyT1)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().toEntity(String.class);
        assertThat(promptT1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promptT1.getBody()).contains("tenant-a-content-" + suffix)
                .doesNotContain("tenant-b-content-" + suffix);

        ResponseEntity<String> promptT2 = restClient.post()
                .uri("/v1/prompts/shared-prompt/render")
                .header("Authorization", "Bearer " + keyT2)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().toEntity(String.class);
        assertThat(promptT2.getBody()).contains("tenant-b-content-" + suffix);
    }

    @Test
    void virtual_key_gets_403_on_admin_endpoints() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String key = generateKey("tenant-a-" + suffix, "project-a-" + suffix);

        ResponseEntity<String> response = restClient.get().uri("/v1/admin/keys")
                .header("Authorization", "Bearer " + key)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("authorization_error");
    }

    @Test
    void call_log_records_tenant_and_project() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-a-" + suffix;
        String project = "project-a-" + suffix;
        String key = generateKey(tenant, project);
        String keyHash = com.qtua.aihub.common.util.KeyHasher.sha256Hex(key);
        ResponseEntity<String> response = restClient.post().uri("/v1/memory/save")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"user_id\":\"u9\",\"content\":\"logged\"}")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<com.qtua.aihub.dal.model.CallLogDO> logs = java.util.List.of();
        for (int attempt = 0; attempt < 20; attempt++) {
            logs = callLogMapper.selectRecentByKeyId(keyHash, 10);
            if (!logs.isEmpty()) {
                break;
            }
            Thread.sleep(50L);
        }
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getTenantId()).isEqualTo(tenant);
        assertThat(logs.get(0).getProjectId()).isEqualTo(project);
    }

    private String generateKey(String tenantId, String projectId) throws Exception {
        ResponseEntity<String> response = restClient.post().uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"models\":[],\"tenant_id\":\"" + tenantId
                        + "\",\"project_id\":\"" + projectId + "\"}")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extractField(response.getBody(), "key");
    }

    private String extractField(String body, String field) {
        try {
            JsonNode node = MAPPER.readTree(body);
            return node.path(field).asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void publishPrompt(String tenantId, String name, String content) {
        PromptTemplateBO prompt = new PromptTemplateBO(name, 1, content, List.of(),
                "PUBLISHED", tenantId, "test", Instant.now());
        promptManager.save(prompt);
    }

    private MultiValueMap<String, Object> uploadBody(String fileName, String content,
                                                    String projectId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("project_id", projectId);
        return body;
    }
}
