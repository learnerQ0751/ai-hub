package com.qtua.aihub;

import com.qtua.aihub.gateway.GatewayApplication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.mapper.MemoryAuditMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import com.qtua.aihub.dal.model.MemoryAuditDO;
import com.qtua.aihub.api.KeyService;
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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.5 端到端验收：保存/召回/清理、用户与项目隔离、TTL、删除审计与调用日志。
 *
 * <p>使用 H2（PostgreSQL 兼容模式），不依赖本机 PostgreSQL/Docker。
 * 默认配置下跨项目共享关闭，验证默认隔离与授权拒绝。</p>
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MemoryServiceIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private KeyService keyService;

    private final java.util.Map<String, String> keys = new java.util.HashMap<>();

    /**
     * 按请求作用域取虚拟 Key：拆分后 Key 绑定 (tenant_id, project_id)。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @return 明文 Key
     */
    private String keyFor(String tenantId, String projectId) {
        return keys.computeIfAbsent(tenantId + "/" + projectId,
                ignored -> TestKeys.create(keyService, tenantId, projectId));
    }

    private static final String ADMIN_KEY = "integration-master";
    private static final String TENANT = "tenant-a";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private MemoryAuditMapper memoryAuditMapper;

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_memory;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> ADMIN_KEY);
    }

    @Test
    void save_recall_clear_and_physical_delete_audit() throws Exception {
        ResponseEntity<String> saved = save(TENANT, "p-clear", "u1", "s1",
                "用户对芒果过敏", Map.of("source", "chat"), 3600L);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode savedBody = readJson(saved);
        String memoryId = savedBody.path("memory_id").asText();
        assertThat(memoryId).isNotBlank();
        assertThat(savedBody.path("memory_type").asText()).isEqualTo("SESSION");
        assertThat(savedBody.path("expires_at").isNull()).isFalse();

        JsonNode recalled = readJson(recall(TENANT, "p-clear", "u1", "s1", "芒果", null, null));
        assertThat(recalled.path("count").asInt()).isEqualTo(1);
        JsonNode memory = recalled.path("memories").get(0);
        assertThat(memory.path("memory_id").asText()).isEqualTo(memoryId);
        assertThat(memory.path("content").asText()).isEqualTo("用户对芒果过敏");
        assertThat(memory.path("metadata").path("source").asText()).isEqualTo("chat");

        JsonNode cleared = readJson(clear(TENANT, "p-clear", "u1", "s1", "用户行使删除权"));
        assertThat(cleared.path("deleted").asInt()).isEqualTo(1);
        String auditId = cleared.path("audit_id").asText();
        assertThat(auditId).isNotBlank();

        MemoryAuditDO audit = memoryAuditMapper.selectByAuditId(auditId).orElseThrow();
        assertThat(audit.getTenantId()).isEqualTo(TENANT);
        assertThat(audit.getProjectId()).isEqualTo("p-clear");
        assertThat(audit.getUserId()).isEqualTo("u1");
        assertThat(audit.getSessionId()).isEqualTo("s1");
        assertThat(audit.getOperation()).isEqualTo("CLEAR");
        assertThat(audit.getDeletedCount()).isEqualTo(1);
        assertThat(audit.getMemoryIds()).contains(memoryId);

        assertThat(readJson(recall(TENANT, "p-clear", "u1", "s1", null, null, null))
                .path("count").asInt()).isZero();
    }

    @Test
    void user_and_project_are_isolated_by_default() throws Exception {
        save(TENANT, "p-iso", "u1", null, "用户画像 A", null, null);

        assertThat(readJson(recall(TENANT, "p-iso", "u2", null, null, null, null))
                .path("count").asInt()).isZero();
        assertThat(readJson(recall(TENANT, "p-iso-other", "u1", null, null, null, null))
                .path("count").asInt()).isZero();
    }

    @Test
    void session_and_user_level_memories_are_recalled_separately() throws Exception {
        save(TENANT, "p-level", "u1", "s1", "会话记忆", null, null);
        save(TENANT, "p-level", "u1", null, "用户画像", null, null);

        assertThat(readJson(recall(TENANT, "p-level", "u1", "s1", null, null, null))
                .path("count").asInt()).isEqualTo(1);
        JsonNode userLevel = readJson(recall(TENANT, "p-level", "u1", null, null, null, null));
        assertThat(userLevel.path("count").asInt()).isEqualTo(1);
        assertThat(userLevel.path("memories").get(0).path("content").asText())
                .isEqualTo("用户画像");
    }

    @Test
    void recall_supports_keyword_and_time_range() throws Exception {
        save(TENANT, "p-keyword", "u1", null, "用户对芒果过敏", null, null);
        save(TENANT, "p-keyword", "u1", null, "用户偏好深色主题", null, null);

        JsonNode keyword = readJson(recall(TENANT, "p-keyword", "u1", null, "芒果", null, null));
        assertThat(keyword.path("count").asInt()).isEqualTo(1);
        assertThat(keyword.path("memories").get(0).path("content").asText()).contains("芒果");

        assertThat(readJson(recall(TENANT, "p-keyword", "u1", null, null,
                "2099-01-01T00:00:00Z", null)).path("count").asInt()).isZero();
        assertThat(readJson(recall(TENANT, "p-keyword", "u1", null, null,
                null, "2000-01-01T00:00:00Z")).path("count").asInt()).isZero();
        assertThat(readJson(recall(TENANT, "p-keyword", "u1", null, null,
                "2000-01-01T00:00:00Z", "2099-01-01T00:00:00Z")).path("count").asInt())
                .isEqualTo(2);
    }

    @Test
    void expired_memory_is_not_recalled() throws Exception {
        save(TENANT, "p-ttl", "u1", null, "短期记忆", null, 1L);
        assertThat(readJson(recall(TENANT, "p-ttl", "u1", null, null, null, null))
                .path("count").asInt()).isEqualTo(1);

        Thread.sleep(1200L);

        assertThat(readJson(recall(TENANT, "p-ttl", "u1", null, null, null, null))
                .path("count").asInt()).isZero();
    }

    @Test
    void cross_project_request_is_rejected_when_sharing_disabled() throws Exception {
        save(TENANT, "p-cross", "u1", null, "跨项目画像", null, null);

        ResponseEntity<String> response = recallWithCrossProject(TENANT, "p-cross-other", "u1",
                null, "画像");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode error = readJson(response).path("error");
        assertThat(error.path("message").asText()).contains("跨项目");
        assertThat(error.path("type").asText()).isEqualTo("memory_access_denied");
        assertThat(error.path("param").asText()).isEqualTo("cross_project");
        assertThat(error.path("code").asText()).isEqualTo("403");
    }

    @Test
    void memory_call_is_logged_with_call_type_and_zero_cost() throws Exception {
        ResponseEntity<String> saved = save(TENANT, "p-log", "u1", "s1", "记忆", null, null);
        String requestId = saved.getHeaders().getFirst("X-Request-Id");
        assertThat(requestId).isNotBlank();

        CallLogDO log = awaitLog(requestId);
        assertThat(log.getCallType()).isEqualTo("MEMORY");
        assertThat(log.getTargetName()).isEqualTo("save");
        assertThat(log.getModelAlias()).isEqualTo("save");
        assertThat(log.getCost()).isEqualByComparingTo("0");
        assertThat(log.getStatus()).isEqualTo(200);
    }

    @Test
    void invalid_request_returns_chinese_message_with_english_type() {
        ResponseEntity<String> response = post("/v1/memory/save", keyFor(TENANT, "p-invalid"),
                saveBody(TENANT, "p-invalid", " ", null, "x", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = readJson(response).path("error");
        assertThat(error.path("message").asText()).isNotBlank().contains("user_id");
        assertThat(error.path("type").asText()).isEqualTo("invalid_request_error");
        assertThat(error.path("param").asText()).isEqualTo("user_id");
    }

    private ResponseEntity<String> save(String tenantId, String projectId, String userId,
                                        String sessionId, String content,
                                        Map<String, Object> metadata, Long ttlSeconds) {
        return post("/v1/memory/save", keyFor(tenantId, projectId),
                saveBody(tenantId, projectId, userId, sessionId, content, metadata, ttlSeconds));
    }

    private String saveBody(String tenantId, String projectId, String userId, String sessionId,
                            String content, Map<String, Object> metadata, Long ttlSeconds) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("tenant_id", tenantId);
            body.put("project_id", projectId);
            body.put("user_id", userId);
            body.put("session_id", sessionId);
            body.put("content", content);
            body.put("metadata", metadata);
            body.put("ttl_seconds", ttlSeconds);
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build save request", ex);
        }
    }

    private ResponseEntity<String> recall(String tenantId, String projectId, String userId,
                                          String sessionId, String keyword, String from,
                                          String to) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("tenant_id", tenantId);
        body.put("project_id", projectId);
        body.put("user_id", userId);
        body.put("session_id", sessionId);
        body.put("keyword", keyword);
        body.put("from", from);
        body.put("to", to);
        body.put("cross_project", false);
        return post("/v1/memory/recall", keyFor(tenantId, projectId), toJson(body));
    }

    private ResponseEntity<String> recallWithCrossProject(String tenantId, String projectId,
                                                          String userId, String sessionId,
                                                          String keyword) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("tenant_id", tenantId);
        body.put("project_id", projectId);
        body.put("user_id", userId);
        body.put("session_id", sessionId);
        body.put("keyword", keyword);
        body.put("cross_project", true);
        return post("/v1/memory/recall", keyFor(tenantId, projectId), toJson(body));
    }

    private ResponseEntity<String> clear(String tenantId, String projectId, String userId,
                                         String sessionId, String reason) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("tenant_id", tenantId);
        body.put("project_id", projectId);
        body.put("user_id", userId);
        body.put("session_id", sessionId);
        body.put("reason", reason);
        return post("/v1/memory/clear", keyFor(tenantId, projectId), toJson(body));
    }

    private ResponseEntity<String> post(String path, String token, String body) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token))
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build request", ex);
        }
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + response.getBody(), ex);
        }
    }

    private CallLogDO awaitLog(String requestId) {
        for (int i = 0; i < 50; i++) {
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
