package com.qtua.aihub;

import com.qtua.aihub.console.ConsoleApplication;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 读取/发布接口 tenant_id 查询参数验收：master 可为指定租户操作。
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PromptTenantFilterIntegrationTest {

    private static final String MASTER_KEY = "prompt-tenant-master";
    private static final String TENANT = "tenant-prompt";

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_prompt_tenant;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
    }

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @Test
    void reads_and_publishes_prompt_for_explicit_tenant() {
        ResponseEntity<String> created = restClient.post().uri("/v1/admin/prompts")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "tenant_prompt", "content", "User: hi",
                        "variables", java.util.List.of(), "tenantId", TENANT))
                .retrieve().toEntity(String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> published = restClient.post()
                .uri("/v1/admin/prompts/tenant_prompt/publish?tenant_id=" + TENANT)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("version", 1))
                .retrieve().toEntity(String.class);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody()).contains(TENANT);

        ResponseEntity<String> scoped = restClient.get()
                .uri("/v1/admin/prompts/tenant_prompt?tenant_id=" + TENANT)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
        assertThat(scoped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scoped.getBody()).contains(TENANT).contains("\"version\":1");

        ResponseEntity<String> versions = restClient.get()
                .uri("/v1/admin/prompts/tenant_prompt/versions?tenant_id=" + TENANT)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
        assertThat(versions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(versions.getBody()).contains("\"versions\"");

        ResponseEntity<String> defaultTenant = restClient.get()
                .uri("/v1/admin/prompts/tenant_prompt")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
        assertThat(defaultTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
