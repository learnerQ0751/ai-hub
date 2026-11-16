package com.qtua.aihub;

import com.qtua.aihub.console.ConsoleApplication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.util.KeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * REQ-4.5 端到端验收：Key 封禁、项目授权、Prompt 发布产生可查审计；
 * 字段完整（actor/action/target/before/after/trace_id）；非管理员不可查。
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditLogIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    }

    @Test
    void key_block_and_authorization_are_audited() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-audit-" + suffix;
        String projectA = "project-a-" + suffix;
        String projectB = "project-b-" + suffix;
        String key = generateKey(tenant, projectA);
        String hash = KeyHasher.sha256Hex(key);

        ResponseEntity<String> block = restClient.post()
                .uri("/v1/admin/keys/" + hash + "/block")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().toEntity(String.class);
        assertThat(block.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> authorize = restClient.post()
                .uri("/v1/admin/project-authorizations")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"tenant_id\":\"" + tenant + "\",\"source_project_id\":\"" + projectA
                        + "\",\"target_project_id\":\"" + projectB
                        + "\",\"resource_type\":\"MEMORY\"}")
                .retrieve().toEntity(String.class);
        assertThat(authorize.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode audits = objectMapper.readTree(restClient.get()
                .uri("/v1/audit-logs?tenant_id=" + tenant + "&limit=20")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class).getBody());
        assertThat(audits.path("items")).isNotEmpty();
        JsonNode blockAudit = findAction(audits, "KEY_BLOCK");
        assertThat(blockAudit.path("actor").asText()).isEqualTo("master");
        assertThat(blockAudit.path("target").asText()).isEqualTo(hash);
        assertThat(blockAudit.path("before").path("blocked").asBoolean()).isFalse();
        assertThat(blockAudit.path("after").path("blocked").asBoolean()).isTrue();
        assertThat(blockAudit.path("trace_id").asText()).isNotBlank();
        assertThat(findAction(audits, "PROJECT_AUTHORIZE")).isNotNull();
        assertThat(findAction(audits, "KEY_GENERATE")).isNotNull();
    }

    @Test
    void virtual_key_cannot_query_audit_logs() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String key = generateKey("tenant-audit-" + suffix, "project-audit-" + suffix);

        ResponseEntity<String> response = restClient.get().uri("/v1/audit-logs")
                .header("Authorization", "Bearer " + key)
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private JsonNode findAction(JsonNode audits, String action) {
        for (JsonNode item : audits.path("items")) {
            if (action.equals(item.path("action").asText())) {
                return item;
            }
        }
        return null;
    }

    private String generateKey(String tenantId, String projectId) throws Exception {
        ResponseEntity<String> response = restClient.post().uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"models\":[],\"tenant_id\":\"" + tenantId
                        + "\",\"project_id\":\"" + projectId + "\"}")
                .retrieve().toEntity(String.class);
        return objectMapper.readTree(response.getBody()).path("key").asText();
    }
}
