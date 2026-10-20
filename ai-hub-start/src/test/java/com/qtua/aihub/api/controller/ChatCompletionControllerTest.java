package com.qtua.aihub.api.controller;

import com.qtua.aihub.api.error.GatewayExceptionHandler;
import com.qtua.aihub.api.filter.AuthFilter;
import com.qtua.aihub.api.filter.RequestIdFilter;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.config.JacksonConfig;
import com.qtua.aihub.service.ChatCompletionService;
import com.qtua.aihub.start.AiHubApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatCompletionController.class)
@ContextConfiguration(classes = AiHubApplication.class)
@Import({RequestIdFilter.class, AuthFilter.class, GatewayExceptionHandler.class, JacksonConfig.class})
class ChatCompletionControllerTest {

    private static final String CHAT_BODY = """
            {"model":"chat-default","messages":[{"role":"user","content":"hi"}]}
            """;

    private static final String OPENAI_RESPONSE = """
            {"id":"x","object":"chat.completion","model":"chat-default",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatCompletionService chatCompletionService;

    @MockitoBean
    private GatewayProperties gatewayProperties;

    @Test
    void returns_openai_response_and_preserves_fields() throws Exception {
        when(chatCompletionService.chat(anyString())).thenReturn(OPENAI_RESPONSE);

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.content").value("Hi"))
                .andExpect(jsonPath("$.usage.total_tokens").value(2));
    }

    @Test
    void accepts_path_without_v1_prefix() throws Exception {
        when(chatCompletionService.chat(anyString())).thenReturn(OPENAI_RESPONSE);

        mockMvc.perform(post("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"));
    }

    @Test
    void unknown_model_returns_openai_error_body() throws Exception {
        when(chatCompletionService.chat(anyString()))
                .thenThrow(ChatCompletionException.invalidModel("no-such-model"));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"no-such-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.param").value("model"))
                .andExpect(jsonPath("$.error.code").value("400"));
    }

    @Test
    void rejects_streaming_for_now() throws Exception {
        when(chatCompletionService.chat(anyString()))
                .thenThrow(ChatCompletionException.invalidRequest("Streaming is not supported yet", "stream"));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"chat-default\",\"stream\":true,"
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.param").value("stream"));
    }

    @Test
    void requires_bearer_token_when_master_key_is_configured() throws Exception {
        when(gatewayProperties.getMasterKey()).thenReturn("secret-master");

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("auth_error"));
    }
}
