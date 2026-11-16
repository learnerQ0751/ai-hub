package com.qtua.aihub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.gateway.GatewayApplication;
import com.qtua.aihub.manager.model.TraceEventBO;
import com.qtua.aihub.manager.trace.TraceEventManager;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-4.2 / REQ-5.1 验收：一次 RAG 请求产生 gateway / rag / model / answer 分段，
 * 事件带租户且不串租户。查询端点归控制面，数据面测试直接读事件表。
 */
@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TraceIntegrationTest {

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

    @Autowired
    private com.qtua.aihub.api.KeyService keyService;

    @Autowired
    private com.qtua.aihub.core.rag.RagDocumentCore ragDocumentCore;

    @Autowired
    private TraceEventManager traceEventManager;

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
        registry.add("gateway.master-key", () -> "trace-admin-key");
        registry.add("gateway.model-list.chat[0].api-base", () -> UPSTREAM.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "upstream-key");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-chat");
    }

    @Test
    void rag_answer_trace_contains_all_segments_and_isolates_tenants() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String tenant = "tenant-trace-" + suffix;
        String project = "project-trace-" + suffix;
        String key = TestKeys.create(keyService, tenant, project);

        ragDocumentCore.ingest(tenant, project, "leave.txt", "text/plain",
                "员工年假为 5 天。".getBytes(java.nio.charset.StandardCharsets.UTF_8), false);

        ResponseEntity<String> answer = restClient.post().uri("/v1/rag/answer")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"query\":\"年假几天\",\"project_id\":\"" + project
                        + "\",\"model\":\"chat\"}")
                .retrieve().toEntity(String.class);
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getBody()).contains("5 天");
        String traceId = answer.getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).isNotBlank();

        List<TraceEventBO> events = awaitTrace(traceId);
        List<String> segments = new ArrayList<>();
        events.forEach(event -> segments.add(event.segment()));
        assertThat(segments).contains("gateway", "rag", "model", "answer");
        assertThat(events).allSatisfy(event -> assertThat(event.tenantId()).isEqualTo(tenant));
    }

    private List<TraceEventBO> awaitTrace(String traceId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            List<TraceEventBO> events = traceEventManager.findByTraceId(traceId);
            if (events.size() >= 4) {
                return events;
            }
            Thread.sleep(50L);
        }
        return traceEventManager.findByTraceId(traceId);
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
