package com.qtua.aihub.integration;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstreamClientTest {

    private MockWebServer server;
    private UpstreamClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new UpstreamClient(RestClient.builder(), WebClient.builder());
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
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value())
                            .isEqualTo(HttpStatus.UNAUTHORIZED.value());
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void maps_server_error_to_retryable_upstream_error() {
        server.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        assertThatThrownBy(() -> client.chat(deployment(server.url("/").toString()), "{}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value())
                            .isEqualTo(HttpStatus.BAD_GATEWAY.value());
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void maps_429_to_retryable_error() {
        server.enqueue(new MockResponse().setResponseCode(HttpStatus.TOO_MANY_REQUESTS.value()));

        assertThatThrownBy(() -> client.chat(deployment(server.url("/").toString()), "{}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).isRetryable()).isTrue());
    }

    @Test
    void maps_4xx_to_non_retryable_error() {
        server.enqueue(new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value()));

        assertThatThrownBy(() -> client.chat(deployment(server.url("/").toString()), "{}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).isRetryable()).isFalse());
    }

    @Test
    void streams_sse_data_events() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"a\":1}\n\ndata: [DONE]\n\n"));

        List<String> events = client.stream(deployment(server.url("/").toString()), "{}")
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).containsExactly("{\"a\":1}", "[DONE]");
    }

    @Test
    void maps_stream_auth_error() {
        server.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));

        assertThatThrownBy(() -> client.stream(deployment(server.url("/").toString()), "{}")
                .blockLast(Duration.ofSeconds(5)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getStatus().value())
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    private ModelDeployment deployment(String baseUrl) {
        return new ModelDeployment("chat", baseUrl, "test-key", "deepseek-chat", 1, 0);
    }
}
