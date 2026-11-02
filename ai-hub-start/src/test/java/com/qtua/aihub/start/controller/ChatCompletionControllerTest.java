package com.qtua.aihub.start.controller;

import com.qtua.aihub.start.error.GatewayExceptionHandler;
import com.qtua.aihub.start.filter.AuthFilter;
import com.qtua.aihub.start.filter.RequestIdFilter;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.start.config.JacksonConfig;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.service.gateway.GatewayGovernanceService;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.ChatCompletionStreamResDTO;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.AiHubApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private KeyService keyService;

    @MockitoBean
    private GatewayProperties gatewayProperties;

    @MockitoBean
    private GatewayGovernanceService gatewayGovernanceService;

    @Test
    void returns_openai_response_and_preserves_fields() throws Exception {
        when(chatCompletionService.chat(any(ChatCompletionReqDTO.class)))
                .thenReturn(new ChatCompletionResDTO(OPENAI_RESPONSE));

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
    void passes_request_id_to_service() throws Exception {
        when(chatCompletionService.chat(any(ChatCompletionReqDTO.class)))
                .thenReturn(new ChatCompletionResDTO(OPENAI_RESPONSE));

        MvcResult result = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isOk())
                .andReturn();

        ArgumentCaptor<ChatCompletionReqDTO> captor =
                ArgumentCaptor.forClass(ChatCompletionReqDTO.class);
        verify(chatCompletionService).chat(captor.capture());
        assertThat(captor.getValue().getRequestId())
                .isEqualTo(result.getResponse().getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void accepts_path_without_v1_prefix() throws Exception {
        when(chatCompletionService.chat(any(ChatCompletionReqDTO.class)))
                .thenReturn(new ChatCompletionResDTO(OPENAI_RESPONSE));

        mockMvc.perform(post("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"));
    }

    @Test
    void unknown_model_returns_openai_error_body() throws Exception {
        when(chatCompletionService.chat(any(ChatCompletionReqDTO.class)))
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
    void streams_sse_events() throws Exception {
        when(chatCompletionService.chatStream(any(ChatCompletionReqDTO.class)))
                .thenReturn(new ChatCompletionStreamResDTO(
                        Flux.just("{\"model\":\"chat-default\"}", "[DONE]"),
                        new AtomicReference<>(new TokenUsage(10, 20, 30)),
                        "chat-default", "upstream"));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"chat-default\",\"stream\":true,"
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("[DONE]")));
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

    @Test
    void returns_403_when_model_is_not_allowed_for_key() throws Exception {
        when(chatCompletionService.chat(any(ChatCompletionReqDTO.class)))
                .thenThrow(ChatCompletionException.keyModelAccessDenied("chat-default"));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.type").value("key_model_access_denied"))
                .andExpect(jsonPath("$.error.param").value("model"))
                .andExpect(jsonPath("$.error.code").value("403"));
    }
}
