package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.console.ConsoleApplication;
import com.qtua.aihub.core.ChatCompletionCore;
import com.qtua.aihub.core.mcp.McpToolAggregator;
import com.qtua.aihub.core.rag.RagRetrievalCore;
import com.qtua.aihub.gateway.filter.AuthFilter;
import com.qtua.aihub.gateway.filter.GatewayGovernanceFilter;
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
 * REQ-5.1 控制面隔离验收。
 *
 * <p>断言控制台进程不装载聊天/检索运行时与业务鉴权过滤器；虚拟 Key 打 console 403，
 * master key 正常放行；数据面端点不在 console 暴露。</p>
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConsoleIsolationTest {

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
    void data_plane_services_and_filters_are_not_loaded() {
        assertThat(context.getBeanNamesForType(ChatCompletionService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ChatCompletionCore.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RagRetrievalCore.class)).isEmpty();
        assertThat(context.getBeanNamesForType(McpToolAggregator.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AuthFilter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GatewayGovernanceFilter.class)).isEmpty();
    }

    @Test
    void admin_key_is_accepted_on_console() {
        ResponseEntity<String> response = get("/v1/admin/keys?tenant_id=isolation-tenant", ADMIN_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void virtual_key_is_forbidden_on_console() throws Exception {
        ResponseEntity<String> response = get("/v1/admin/keys?tenant_id=isolation-tenant", dataKey);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode error = objectMapper.readTree(response.getBody()).path("error");
        assertThat(error.path("type").asText()).isEqualTo("authorization_error");
    }

    @Test
    void data_plane_endpoint_is_not_exposed_by_console() {
        ResponseEntity<String> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(ADMIN_KEY))
                .body(CHAT_BODY)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void health_endpoint_is_up() {
        ResponseEntity<String> response = get("/actuator/health", ADMIN_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private ResponseEntity<String> get(String path, String token) {
        return restClient.get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toEntity(String.class);
    }
}
