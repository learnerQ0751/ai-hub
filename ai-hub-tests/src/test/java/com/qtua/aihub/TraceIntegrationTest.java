package com.qtua.aihub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-4.2 端到端验收：一次 RAG 请求可通过 trace_id 查到
 * gateway / rag / model / answer 分段，且响应头与查询一致。
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TraceIntegrationTest {

    private static final String MASTER_KEY = "integration-master";
    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream",
             "choices":[{"index":0,"message":{"role":"assistant","content":"年假为 5 天。"},
             "finish_reason":"stop"}],
             "usage":{"prompt_tokens":8,"completion_tokens":6,"total_tokens":14}}
            """;
    private static final MockWebServer UPSTREAM = startServer();
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

    @AfterAll
    static void stopServer() throws IOException {
        UPSTREAM.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
    }

    @Test
    void rag_answer_trace_contains_all_segments_and_isolates_tenants() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String key = generateKey("tenant-trace-" + suffix, "project-trace-" + suffix);

        ResponseEntity<String> upload = restClient.post().uri("/v1/rag/documents")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(uploadBody("leave.txt", "员工年假为 5 天。", "project-trace-" + suffix))
                .retrieve().toEntity(String.class);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> answer = restClient.post().uri("/v1/rag/answer")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"query\":\"年假几天\",\"project_id\":\"project-trace-" + suffix
                        + "\",\"model\":\"chat\"}")
                .retrieve().toEntity(String.class);
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        String traceId = answer.getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).isNotBlank();

        JsonNode trace = awaitTrace(key, traceId);
        assertThat(trace.path("trace_id").asText()).isEqualTo(traceId);
        List<String> segments = new ArrayList<>();
        trace.path("events").forEach(event -> segments.add(event.path("segment").asText()));
        assertThat(segments).contains("gateway", "rag", "model", "answer");

        String otherKey = generateKey("tenant-other-" + suffix, "project-other-" + suffix);
        ResponseEntity<String> forbidden = restClient.get().uri("/v1/traces/" + traceId)
                .header("Authorization", "Bearer " + otherKey)
                .retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode awaitTrace(String key, String traceId) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            ResponseEntity<String> response = restClient.get().uri("/v1/traces/" + traceId)
                    .header("Authorization", "Bearer " + key)
                    .retrieve().toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return objectMapper.readTree(response.getBody());
            }
            Thread.sleep(50L);
        }
        throw new IllegalStateException("trace not available: " + traceId);
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

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setHeader("Content-Type", "application/json")
                        .setBody(UPSTREAM_RESPONSE);
            }
        });
        try {
            server.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start mock upstream", ex);
        }
        return server;
    }
}
