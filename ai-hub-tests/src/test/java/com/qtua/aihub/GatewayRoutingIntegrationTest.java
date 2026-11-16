package com.qtua.aihub;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import com.qtua.aihub.gateway.GatewayApplication;
import com.qtua.aihub.api.KeyService;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private KeyService keyService;

    private String dataKey;

    /**
     * 数据面测试统一使用虚拟 Key（master key 仅控制面可用）。
     */
    @org.junit.jupiter.api.BeforeEach
    void initDataPlaneKey() {
        dataKey = TestKeys.create(keyService, "test-tenant", "test-project");
    }

    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private static final MockWebServer FIRST = startServer();
    private static final MockWebServer SECOND = startServer();

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> "");
        registry.add("gateway.model-list.chat[0].api-base", () -> FIRST.url("/").toString());
        registry.add("gateway.model-list.chat[0].api-key", () -> "key-a");
        registry.add("gateway.model-list.chat[0].model", () -> "upstream-a");
        registry.add("gateway.model-list.chat[0].rpm", () -> "3");
        registry.add("gateway.model-list.chat[1].api-base", () -> SECOND.url("/").toString());
        registry.add("gateway.model-list.chat[1].api-key", () -> "key-b");
        registry.add("gateway.model-list.chat[1].model", () -> "upstream-b");
        registry.add("gateway.model-list.chat[1].rpm", () -> "1");
        registry.add("gateway.model-list.reasoner[0].api-base", () -> FIRST.url("/").toString());
        registry.add("gateway.model-list.reasoner[0].api-key", () -> "key-a");
        registry.add("gateway.model-list.reasoner[0].model", () -> "upstream-reasoner");
    }

    @AfterAll
    static void stopServers() throws IOException {
        FIRST.shutdown();
        SECOND.shutdown();
    }

    @Test
    void routes_requests_by_weight() {
        int firstBefore = FIRST.getRequestCount();
        int secondBefore = SECOND.getRequestCount();
        String request = "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        for (int i = 0; i < 60; i++) {
            ResponseEntity<String> response = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"model\":\"chat\"");
        }

        int firstDelta = FIRST.getRequestCount() - firstBefore;
        int secondDelta = SECOND.getRequestCount() - secondBefore;
        assertThat(firstDelta).isGreaterThan(0);
        assertThat(secondDelta).isGreaterThan(0);
        assertThat(firstDelta).isGreaterThan(secondDelta);
    }

    @Test
    void lists_only_aliases() {
        ResponseEntity<String> response = restClient.get()
                .uri("/models")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"id\":\"chat\"", "\"id\":\"reasoner\"")
                .doesNotContain("upstream-a", "upstream-b", "api-base", "api-key", "rpm");
    }

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
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
