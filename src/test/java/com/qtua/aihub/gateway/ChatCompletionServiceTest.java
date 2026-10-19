package com.qtua.aihub.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.config.GatewayProperties;
import com.qtua.aihub.config.GatewayProperties.Deployment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCompletionServiceTest {

    private GatewayProperties properties;
    private UpstreamClient upstreamClient;
    private ChatCompletionService service;

    @BeforeEach
    void setUp() {
        Deployment deployment = new Deployment();
        deployment.setApiBase("https://example.com");
        deployment.setApiKey("test-key");
        deployment.setModel("deepseek-flash");

        properties = mock(GatewayProperties.class);
        when(properties.getModelList()).thenReturn(java.util.Map.of("chat-default", List.of(deployment)));

        upstreamClient = mock(UpstreamClient.class);
        when(upstreamClient.chat(any(), any())).thenReturn("{\"object\":\"chat.completion\"}");

        service = new ChatCompletionService(properties, upstreamClient, new ObjectMapper());
    }

    @Test
    void rewrites_alias_to_upstream_model_before_forwarding() {
        String body = "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        service.chat(body);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(upstreamClient).chat(any(Deployment.class), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("\"model\":\"deepseek-flash\"")
                .doesNotContain("\"model\":\"chat-default\"");
    }

    @Test
    void rewrites_response_model_to_alias() {
        String body = "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
        when(upstreamClient.chat(any(), any()))
                .thenReturn("{\"object\":\"chat.completion\",\"model\":\"deepseek-flash\"}");

        String response = service.chat(body);

        assertThat(response).contains("\"model\":\"chat-default\"")
                .doesNotContain("\"model\":\"deepseek-flash\"");
    }

    @Test
    void rejects_unknown_model() {
        assertThatThrownBy(() -> service.chat(
                "{\"model\":\"no-such-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("invalid_request_error"));
    }

    @Test
    void rejects_streaming() {
        assertThatThrownBy(() -> service.chat(
                "{\"model\":\"chat-default\",\"stream\":true,"
                        + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("stream"));
    }

    @Test
    void rejects_empty_messages() {
        assertThatThrownBy(() -> service.chat("{\"model\":\"chat-default\",\"messages\":[]}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("messages"));
    }

    @Test
    void rejects_invalid_json() {
        assertThatThrownBy(() -> service.chat("not-json"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("invalid_request_error"));
    }
}
