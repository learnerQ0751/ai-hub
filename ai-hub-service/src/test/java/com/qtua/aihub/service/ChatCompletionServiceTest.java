package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.core.ChatCompletionCore;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCompletionServiceTest {

    private static final KeyAuthDTO MASTER = KeyAuthDTO.master();

    private ModelDeployment deployment;
    private ChatCompletionCore core;
    private ChatCompletionServiceImpl service;

    @BeforeEach
    void setUp() {
        deployment = new ModelDeployment("chat-default", "https://example.com", "test-key",
                "deepseek-flash", 1);

        ModelCatalog modelCatalog = mock(ModelCatalog.class);
        when(modelCatalog.findDeployments("chat-default")).thenReturn(List.of(deployment));

        core = mock(ChatCompletionCore.class);
        when(core.chat(any(ChatCompletionBO.class)))
                .thenReturn(new ChatCompletionResultBO("{\"object\":\"chat.completion\"}"));

        service = new ChatCompletionServiceImpl(modelCatalog, core, new ObjectMapper());
    }

    @Test
    void parses_validates_and_delegates_to_core() {
        String body = "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        ChatCompletionResDTO response = service.chat(new ChatCompletionReqDTO(body, MASTER));

        assertThat(response.getRawResponse()).contains("chat.completion");
        ArgumentCaptor<ChatCompletionBO> boCaptor = ArgumentCaptor.forClass(ChatCompletionBO.class);
        verify(core).chat(boCaptor.capture());
        ChatCompletionBO bo = boCaptor.getValue();
        assertThat(bo.getAlias()).isEqualTo("chat-default");
        assertThat(bo.getRequestBody()).contains("\"model\":\"chat-default\"");
        assertThat(bo.getDeployments()).containsExactly(deployment);
        assertThat(bo.isMasterKey()).isTrue();
    }

    @Test
    void passes_virtual_key_whitelist_to_core() {
        String body = "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
        KeyAuthDTO virtualKey = KeyAuthDTO.virtualKey("hash", List.of("chat-default"));

        service.chat(new ChatCompletionReqDTO(body, virtualKey));

        ArgumentCaptor<ChatCompletionBO> boCaptor = ArgumentCaptor.forClass(ChatCompletionBO.class);
        verify(core).chat(boCaptor.capture());
        assertThat(boCaptor.getValue().isMasterKey()).isFalse();
        assertThat(boCaptor.getValue().getAllowedModels()).containsExactly("chat-default");
        assertThat(boCaptor.getValue().getKeyHash()).isEqualTo("hash");
    }

    @Test
    void passes_request_id_to_core() {
        String body = "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        service.chat(new ChatCompletionReqDTO(body, MASTER, "req-42"));

        ArgumentCaptor<ChatCompletionBO> boCaptor = ArgumentCaptor.forClass(ChatCompletionBO.class);
        verify(core).chat(boCaptor.capture());
        assertThat(boCaptor.getValue().getRequestId()).isEqualTo("req-42");
        assertThat(boCaptor.getValue().getKeyHash()).isNull();
    }

    @Test
    void passes_empty_deployments_to_core_for_unknown_model() {
        String body = "{\"model\":\"no-such-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

        service.chat(new ChatCompletionReqDTO(body, MASTER));

        ArgumentCaptor<ChatCompletionBO> boCaptor = ArgumentCaptor.forClass(ChatCompletionBO.class);
        verify(core).chat(boCaptor.capture());
        assertThat(boCaptor.getValue().getDeployments()).isEmpty();
    }

    @Test
    void rejects_streaming() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"model\":\"chat-default\",\"stream\":true,"
                        + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", MASTER)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("stream"));
    }

    @Test
    void rejects_empty_messages() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"model\":\"chat-default\",\"messages\":[]}", MASTER)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("messages"));
    }

    @Test
    void rejects_invalid_json() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO("not-json", MASTER)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("invalid_request_error"));
    }

    @Test
    void rejects_missing_model() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO("{\"messages\":[]}", MASTER)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam()).isEqualTo("model"));
    }
}
