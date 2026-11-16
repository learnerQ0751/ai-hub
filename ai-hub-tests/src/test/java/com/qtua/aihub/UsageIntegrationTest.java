package com.qtua.aihub;

import com.qtua.aihub.console.ConsoleApplication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.util.KeyHasher;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-4.3 端到端验收：用量按租户/项目/模型/Key/天聚合，与逐条日志可复算；
 * 虚拟 Key 不能横向查看其他租户；CSV 导出同口径。
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UsageIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @Autowired
    private CallLogMapper callLogMapper;

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
    void usage_aggregates_by_day_and_tenant_and_isolates_keys() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenantA = "tenant-usage-a-" + suffix;
        String tenantB = "tenant-usage-b-" + suffix;
        String project = "project-usage-" + suffix;
        String keyA = generateKey(tenantA, project);
        String keyB = generateKey(tenantB, project);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();

        String hashA = KeyHasher.sha256Hex(keyA);
        String hashB = KeyHasher.sha256Hex(keyB);
        insert(hashA, tenantA, project, "chat", yesterday, 10, 20, new BigDecimal("0.10"));
        insert(hashA, tenantA, project, "chat", today, 30, 40, new BigDecimal("0.30"));
        insert(hashB, tenantB, project, "chat", today, 100, 200, new BigDecimal("1.00"));

        JsonNode usage = objectMapper.readTree(restClient.get()
                .uri("/v1/usage?tenant_id=" + tenantA + "&from=" + yesterday + "&to=" + today)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class).getBody());

        assertThat(usage.path("totals").path("requests").asLong()).isEqualTo(2L);
        assertThat(usage.path("totals").path("total_tokens").asLong()).isEqualTo(100L);
        assertThat(usage.path("totals").path("cost").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.40"));
        assertThat(usage.path("trend")).hasSize(2);
        assertThat(usage.path("unpriced_models").toString()).contains("upstream-chat");

        // 用量查询归控制面：虚拟 Key 一律 403，admin 凭证按 tenant 过滤。
        ResponseEntity<String> virtualDenied = restClient.get()
                .uri("/v1/usage?from=" + yesterday + "&to=" + today)
                .header("Authorization", "Bearer " + keyA)
                .retrieve().toEntity(String.class);
        assertThat(virtualDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode tenantScoped = objectMapper.readTree(restClient.get()
                .uri("/v1/usage?tenant_id=" + tenantA + "&from=" + yesterday + "&to=" + today)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class).getBody());
        assertThat(tenantScoped.path("totals").path("requests").asLong()).isEqualTo(2L);
        assertThat(tenantScoped.path("tenant_id").asText()).isEqualTo(tenantA);

        ResponseEntity<String> csv = restClient.get()
                .uri("/v1/usage/export?tenant_id=" + tenantA + "&from=" + yesterday
                        + "&to=" + today)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getBody()).contains("date,tenant_id,project_id")
                .contains(tenantA).doesNotContain(tenantB);
    }

    private void insert(String keyHash, String tenantId, String projectId, String alias,
                        LocalDate day, int promptTokens, int completionTokens, BigDecimal cost) {
        CallLogDO log = new CallLogDO();
        log.setRequestId(UUID.randomUUID().toString());
        log.setKeyId(keyHash);
        log.setTenantId(tenantId);
        log.setProjectId(projectId);
        log.setModelAlias(alias);
        log.setDeploymentModel("upstream-chat");
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(promptTokens + completionTokens);
        log.setCost(cost);
        log.setLatencyMs(10L);
        log.setStatus(200);
        log.setTraceId(log.getRequestId());
        log.setCallType("CHAT");
        log.setCreatedAt(day.atTime(12, 0).toInstant(ZoneOffset.UTC));
        callLogMapper.insert(log);
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
