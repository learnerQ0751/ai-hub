package com.qtua.aihub.gateway;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    private static final String UPSTREAM_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"upstream",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private static final MockWebServer FIRST = startServer();
    private static final MockWebServer SECOND = startServer();

    @Autowired
    private TestRestTemplate restTemplate;

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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(request, headers);

        for (int i = 0; i < 16; i++) {
            ResponseEntity<String> response =
                    restTemplate.postForEntity("/v1/chat/completions", entity, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"model\":\"chat\"");
        }

        assertThat(FIRST.getRequestCount() - firstBefore).isEqualTo(12);
        assertThat(SECOND.getRequestCount() - secondBefore).isEqualTo(4);
    }

    @Test
    void lists_only_aliases() {
        ResponseEntity<String> response = restTemplate.getForEntity("/models", String.class);

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
