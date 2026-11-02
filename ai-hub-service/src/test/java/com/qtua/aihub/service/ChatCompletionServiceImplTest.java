package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.core.ChatCompletionCore;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatCompletionServiceImplTest {

    private static final String BODY =
            "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String RESPONSE = "{\"object\":\"chat.completion\"}";
    private static final KeyAuthDTO MASTER = KeyAuthDTO.master();

    private ModelCatalog modelCatalog;
    private ChatCompletionCore chatCompletionCore;
    private ChatCompletionServiceImpl service;
    private ModelDeployment deployment;

    @BeforeEach
    void setUp() {
        modelCatalog = mock(ModelCatalog.class);
        chatCompletionCore = mock(ChatCompletionCore.class);
        deployment = new ModelDeployment("chat-default", "https://example.com", "key",
                "deepseek-flash", 1);
        when(modelCatalog.findDeployments("chat-default")).thenReturn(List.of(deployment));
        when(chatCompletionCore.chat(any())).thenReturn(new ChatCompletionResultBO(
                RESPONSE, new TokenUsage(10, 20, 30), "deepseek-flash"));
        service = new ChatCompletionServiceImpl(modelCatalog, chatCompletionCore, new ObjectMapper());
    }

    @Test
    void parses_validates_and_delegates_to_executor() {
        ChatCompletionResDTO response = service.chat(
                new ChatCompletionReqDTO(BODY, MASTER, "req-1"));

        assertThat(response.getRawResponse()).isEqualTo(RESPONSE);
        assertThat(response.getUsage()).isEqualTo(new TokenUsage(10, 20, 30));
        assertThat(response.getDeploymentModel()).isEqualTo("deepseek-flash");
    }

    @Test
    void rejects_streaming() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"model\":\"chat-default\",\"stream\":true,"
                        + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", MASTER, "req-1")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("stream"));
        verifyNoInteractions(chatCompletionCore);
    }

    @Test
    void rejects_empty_messages() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"model\":\"chat-default\",\"messages\":[]}", MASTER, "req-1")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("messages"));
    }

    @Test
    void rejects_invalid_json() {
        assertThatThrownBy(() -> service.chat(
                new ChatCompletionReqDTO("not-json", MASTER, "req-1")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("invalid_request_error"));
    }

    @Test
    void rejects_missing_model() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", MASTER, "req-1")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("model"));
    }

    @Test
    void rejects_unknown_model() {
        when(modelCatalog.findDeployments("no-such-model")).thenReturn(List.of());

        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"model\":\"no-such-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                MASTER, "req-1")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("model"));
        verifyNoInteractions(chatCompletionCore);
    }
}
