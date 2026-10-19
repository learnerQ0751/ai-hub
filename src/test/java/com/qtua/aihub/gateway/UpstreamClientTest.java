package com.qtua.aihub.gateway;

import com.qtua.aihub.config.GatewayProperties.Deployment;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstreamClientTest {

    private MockWebServer server;
    private UpstreamClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new UpstreamClient(RestClient.builder());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void forwards_request_and_returns_raw_body() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"x\",\"object\":\"chat.completion\",\"usage\":{\"total_tokens\":2}}"));

        String body = client.chat(deployment(server.url("/").toString()),
                "{\"model\":\"chat-default\",\"messages\":[]}");

        assertThat(body).contains("\"object\":\"chat.completion\"")
                .contains("\"usage\":{\"total_tokens\":2}");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    }

    @Test
    void maps_401_to_auth_error() {
        server.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));

        assertThatThrownBy(() -> client.chat(deployment(server.url("/").toString()), "{}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getStatus().value())
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void maps_server_error_to_upstream_error() {
        server.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        assertThatThrownBy(() -> client.chat(deployment(server.url("/").toString()), "{}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getStatus().value())
                        .isEqualTo(HttpStatus.BAD_GATEWAY.value()));
    }

    private Deployment deployment(String baseUrl) {
        Deployment deployment = new Deployment();
        deployment.setApiBase(baseUrl);
        deployment.setApiKey("test-key");
        deployment.setModel("deepseek-chat");
        return deployment;
    }
}
