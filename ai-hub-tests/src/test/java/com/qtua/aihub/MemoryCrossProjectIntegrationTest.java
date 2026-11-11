package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.5 跨项目共享验收：开关开启后，仅用户级记忆可按同一租户跨项目召回。
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MemoryCrossProjectIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String TENANT = "tenant-a";

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_memory_cross;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.memory.cross-project-sharing-enabled", () -> "true");
    }

    @Test
    void user_level_memory_is_shared_across_projects_when_enabled() throws Exception {
        save("project-a", "u-share", null, "用户画像：芒果过敏");

        JsonNode shared = recall("project-b", "u-share", null, true, "画像");
        assertThat(shared.path("count").asInt()).isEqualTo(1);
        assertThat(shared.path("memories").get(0).path("project_id").asText())
                .isEqualTo("project-a");

        JsonNode localOnly = recall("project-b", "u-share", null, false, "画像");
        assertThat(localOnly.path("count").asInt()).isZero();
    }

    @Test
    void session_memory_is_never_shared_across_projects() throws Exception {
        save("project-a", "u-session", "s1", "会话记忆");

        ResponseEntity<String> response = recallResponse("project-b", "u-session", "s1",
                true, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(response).path("error").path("param").asText())
                .isEqualTo("session_id");

        assertThat(recall("project-b", "u-session", "s1", false, null)
                .path("count").asInt()).isZero();
    }

    @Test
    void other_user_memory_is_still_isolated_when_sharing_enabled() throws Exception {
        save("project-a", "u-isolated", null, "用户画像");

        assertThat(recall("project-b", "u-other", null, true, null)
                .path("count").asInt()).isZero();
    }

    private void save(String projectId, String userId, String sessionId, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", TENANT);
        body.put("project_id", projectId);
        body.put("user_id", userId);
        body.put("session_id", sessionId);
        body.put("content", content);
        ResponseEntity<String> response = post("/v1/memory/save", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode recall(String projectId, String userId, String sessionId,
                            boolean crossProject, String keyword) throws Exception {
        return readJson(recallResponse(projectId, userId, sessionId, crossProject, keyword));
    }

    private ResponseEntity<String> recallResponse(String projectId, String userId,
                                                  String sessionId, boolean crossProject,
                                                  String keyword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", TENANT);
        body.put("project_id", projectId);
        body.put("user_id", userId);
        body.put("session_id", sessionId);
        body.put("cross_project", crossProject);
        body.put("keyword", keyword);
        return post("/v1/memory/recall", body);
    }

    private ResponseEntity<String> post(String path, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(MASTER_KEY))
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toEntity(String.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize request", ex);
        }
    }

    private JsonNode readJson(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
