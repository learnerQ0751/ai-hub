package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.common.config.GatewayProperties.Deployment;
import com.qtua.aihub.core.ChatCompletionCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCompletionServiceTest {

    private Deployment deployment;
    private ChatCompletionCore core;
    private ChatCompletionService service;

    @BeforeEach
    void setUp() {
        deployment = new Deployment();
        deployment.setApiBase("https://example.com");
        deployment.setApiKey("test-key");
        deployment.setModel("deepseek-flash");

        GatewayProperties properties = mock(GatewayProperties.class);
        when(properties.getModelList()).thenReturn(Map.of("chat-default", List.of(deployment)));

        core = mock(ChatCompletionCore.class);
        when(core.chat(anyString(), anyList(), any())).thenReturn("{\"object\":\"chat.completion\"}");

        service = new ChatCompletionService(properties, core, new ObjectMapper());
    }

    @Test
    void parses_validates_and_delegates_to_core() {
        String body = "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        String response = service.chat(body);

        assertThat(response).contains("chat.completion");
        ArgumentCaptor<JsonNode> requestCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(core).chat(eq("chat-default"), eq(List.of(deployment)), requestCaptor.capture());
        assertThat(requestCaptor.getValue().get("model").asText()).isEqualTo("chat-default");
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

    @Test
    void rejects_missing_model() {
        assertThatThrownBy(() -> service.chat("{\"messages\":[]}"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("model"));
    }
}
