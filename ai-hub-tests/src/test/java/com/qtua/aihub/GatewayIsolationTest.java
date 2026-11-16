package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.AdminService;
import com.qtua.aihub.api.AuditQueryService;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.api.McpServerService;
import com.qtua.aihub.api.PromptService;
import com.qtua.aihub.api.TraceService;
import com.qtua.aihub.api.UsageService;
import com.qtua.aihub.console.controller.AdminController;
import com.qtua.aihub.console.filter.AdminAuthFilter;
import com.qtua.aihub.gateway.GatewayApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-5.1 数据面隔离验收。
 *
 * <p>断言网关进程不装载控制面服务、管理过滤器与控制器；master key 打数据面 403，
 * 虚拟 Key 正常放行；/actuator/health 可用。</p>
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayIsolationTest {

    private static final String ADMIN_KEY = "isolation-admin-key";
    private static final String CHAT_BODY =
            "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private KeyService keyService;

    private RestClient restClient;
    private String dataKey;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> ADMIN_KEY);
    }

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                })
                .build();
        dataKey = TestKeys.create(keyService, "isolation-tenant", "isolation-project");
    }

    @Test
    void control_plane_services_and_filters_are_not_loaded() {
        assertThat(context.getBeanNamesForType(AdminService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(PromptService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(McpServerService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TraceService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(UsageService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AuditQueryService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AdminAuthFilter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AdminController.class)).isEmpty();
    }

    @Test
    void admin_key_is_forbidden_on_data_plane() throws Exception {
        ResponseEntity<String> response = post("/v1/chat/completions", ADMIN_KEY, CHAT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode error = objectMapper.readTree(response.getBody()).path("error");
        assertThat(error.path("type").asText()).isEqualTo("authorization_error");
    }

    @Test
    void virtual_key_is_accepted_on_data_plane() {
        ResponseEntity<String> response = get("/v1/models", dataKey);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void console_endpoint_is_not_exposed_by_gateway() {
        ResponseEntity<String> response = get("/v1/admin/keys?tenant_id=isolation-tenant", dataKey);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void health_endpoint_is_up() {
        ResponseEntity<String> response = get("/actuator/health", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
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

    private ResponseEntity<String> get(String path, String token) {
        return restClient.get()
                .uri(path)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .retrieve()
                .toEntity(String.class);
    }
}
