package com.qtua.aihub.core;

import com.qtua.aihub.common.config.GatewayProperties.Deployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCompletionCoreTest {

    private static final String REQUEST_BODY =
            "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_BODY =
            "{\"model\":\"deepseek-flash\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_RESPONSE =
            "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"deepseek-flash\",\"choices\":[]}";
    private static final String FINAL_RESPONSE =
            "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"chat\",\"choices\":[]}";

    private Deployment deployment;
    private DeploymentSelector selector;
    private UpstreamClient upstreamClient;
    private OpenAiProtocolAdapter protocolAdapter;
    private ChatCompletionCore core;

    @BeforeEach
    void setUp() {
        deployment = new Deployment();
        deployment.setApiBase("https://example.com");
        deployment.setApiKey("test-key");
        deployment.setModel("deepseek-flash");

        selector = mock(DeploymentSelector.class);
        upstreamClient = mock(UpstreamClient.class);
        protocolAdapter = mock(OpenAiProtocolAdapter.class);
        core = new ChatCompletionCore(selector, upstreamClient, protocolAdapter);

        when(selector.select(eq("chat"), eq(List.of(deployment)))).thenReturn(deployment);
        when(protocolAdapter.rewriteRequestModel(REQUEST_BODY, "deepseek-flash")).thenReturn(UPSTREAM_BODY);
        when(upstreamClient.chat(deployment, UPSTREAM_BODY)).thenReturn(UPSTREAM_RESPONSE);
        when(protocolAdapter.rewriteResponseModel(UPSTREAM_RESPONSE, "chat")).thenReturn(FINAL_RESPONSE);
    }

    @Test
    void orchestrates_routing_protocol_and_upstream_call() {
        ChatCompletionBO bo = new ChatCompletionBO("chat", List.of(deployment), REQUEST_BODY);

        ChatCompletionResultBO result = core.chat(bo);

        assertThat(result.getResponseBody()).isEqualTo(FINAL_RESPONSE);
        verify(selector).select(eq("chat"), eq(List.of(deployment)));
        verify(protocolAdapter).rewriteRequestModel(REQUEST_BODY, "deepseek-flash");
        verify(upstreamClient).chat(deployment, UPSTREAM_BODY);
        verify(protocolAdapter).rewriteResponseModel(UPSTREAM_RESPONSE, "chat");
    }

    @Test
    void propagates_upstream_error() {
        when(upstreamClient.chat(eq(deployment), anyString()))
                .thenThrow(ChatCompletionException.upstreamTimeout(new RuntimeException("timeout")));
        ChatCompletionBO bo = new ChatCompletionBO("chat", List.of(deployment), REQUEST_BODY);

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("timeout_error"));
    }

    @Test
    void propagates_protocol_error() {
        when(protocolAdapter.rewriteResponseModel(UPSTREAM_RESPONSE, "chat"))
                .thenThrow(ChatCompletionException.upstreamError("bad json", null));
        ChatCompletionBO bo = new ChatCompletionBO("chat", List.of(deployment), REQUEST_BODY);

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("upstream_error"));
    }
}
