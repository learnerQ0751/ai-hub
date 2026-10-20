package com.qtua.aihub.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.ChatCompletionException;
import com.qtua.aihub.config.GatewayProperties.Deployment;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCompletionCoreTest {

    private Deployment deployment;
    private DeploymentSelector selector;
    private UpstreamClient upstreamClient;
    private ChatCompletionCore core;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deployment = new Deployment();
        deployment.setApiBase("https://example.com");
        deployment.setApiKey("test-key");
        deployment.setModel("deepseek-flash");

        selector = mock(DeploymentSelector.class);
        upstreamClient = mock(UpstreamClient.class);
        objectMapper = new ObjectMapper();
        core = new ChatCompletionCore(selector, upstreamClient, objectMapper);

        when(selector.select(eq("chat"), eq(List.of(deployment)))).thenReturn(deployment);
        when(upstreamClient.chat(eq(deployment), anyString())).thenReturn("""
                {"id":"x","object":"chat.completion","model":"deepseek-flash",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """);
    }

    @Test
    void rewrites_request_and_response_model() throws Exception {
        JsonNode request = objectMapper.readTree(
                "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");

        String response = core.chat("chat", List.of(deployment), request);

        assertThat(response).contains("\"model\":\"chat\"")
                .contains("\"object\":\"chat.completion\"")
                .contains("\"usage\"");
        verify(selector).select(eq("chat"), eq(List.of(deployment)));
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(upstreamClient).chat(eq(deployment), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("\"model\":\"deepseek-flash\"")
                .doesNotContain("\"model\":\"chat\"");
    }

    @Test
    void propagates_upstream_error() throws Exception {
        when(upstreamClient.chat(eq(deployment), anyString()))
                .thenThrow(ChatCompletionException.upstreamTimeout(new RuntimeException("timeout")));
        JsonNode request = objectMapper.readTree(
                "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");

        assertThatThrownBy(() -> core.chat("chat", List.of(deployment), request))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("timeout_error"));
    }

    @Test
    void rejects_invalid_upstream_json() throws Exception {
        when(upstreamClient.chat(eq(deployment), anyString())).thenReturn("<html>bad gateway</html>");
        JsonNode request = objectMapper.readTree(
                "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");

        assertThatThrownBy(() -> core.chat("chat", List.of(deployment), request))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("upstream_error"));
    }
}
