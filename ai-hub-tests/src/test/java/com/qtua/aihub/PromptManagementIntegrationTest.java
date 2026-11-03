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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.4 端到端验收：Prompt 模板 CRUD、版本、发布/回滚、变量校验与安全。
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PromptManagementIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_prompt;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
    }

    @Test
    void creates_versions_publishes_and_rolls_back() throws Exception {
        ResponseEntity<String> created = create("greet", "User: Hello {{name}}", List.of("name"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(created.getBody()).path("version").asInt()).isEqualTo(1);
        assertThat(objectMapper.readTree(created.getBody()).path("status").asText())
                .isEqualTo("DRAFT");

        // 未发布时查询 → 404
        assertThat(get("greet", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        publish("greet", 1);
        JsonNode v1 = objectMapper.readTree(get("greet", null).getBody());
        assertThat(v1.path("version").asInt()).isEqualTo(1);

        createVersion("greet", "User: Hi {{name}}", List.of("name"));
        publish("greet", 2);
        assertThat(objectMapper.readTree(get("greet", null).getBody()).path("version").asInt())
                .isEqualTo(2);

        // 回滚：重新发布 v1
        publish("greet", 1);
        assertThat(objectMapper.readTree(get("greet", null).getBody()).path("version").asInt())
                .isEqualTo(1);

        // 版本历史
        JsonNode versions = objectMapper.readTree(versions("greet").getBody());
        assertThat(versions.path("versions")).hasSize(2);
    }

    @Test
    void variable_declaration_mismatch_returns_400() {
        ResponseEntity<String> missing = create("bad_vars", "User: Hello {{name}}", List.of());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody()).contains("name");

        ResponseEntity<String> extra = create("bad_vars2", "User: Hello", List.of("name"));
        assertThat(extra.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unsafe_content_is_rejected() {
        ResponseEntity<String> secret = create("leaky",
                "User: use sk-abcdefghijklmnopqrstuvwxyz123456", List.of());
        assertThat(secret.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String longContent = "x".repeat(10001);
        ResponseEntity<String> tooLong = create("too_long", longContent, List.of());
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void publish_missing_prompt_or_version_returns_404() {
        assertThat(publish("no_such", 1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        create("p_one", "User: hi", List.of());
        assertThat(publish("p_one", 9).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void management_requires_master_key_but_render_will_not() throws Exception {
        ResponseEntity<String> generated = restClient.post()
                .uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"models\":[\"chat\"]}")
                .retrieve().toEntity(String.class);
        String virtualKey = objectMapper.readTree(generated.getBody()).path("key").asText();

        ResponseEntity<String> list = restClient.get()
                .uri("/v1/prompts")
                .header("Authorization", "Bearer " + virtualKey)
                .retrieve().toEntity(String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> create(String name, String content, List<String> variables) {
        return restClient.post()
                .uri("/v1/prompts")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "content", content, "variables", variables))
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> createVersion(String name, String content, List<String> variables) {
        return restClient.post()
                .uri("/v1/prompts/" + name + "/versions")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content, "variables", variables))
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> publish(String name, int version) {
        return restClient.post()
                .uri("/v1/prompts/" + name + "/publish")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("version", version))
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> get(String name, Integer version) {
        String uri = "/v1/prompts/" + name + (version == null ? "" : "?version=" + version);
        return restClient.get().uri(uri)
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> versions(String name) {
        return restClient.get().uri("/v1/prompts/" + name + "/versions")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .retrieve().toEntity(String.class);
    }
}
