package com.qtua.aihub.core;

import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.resilience.DeploymentHealthStore;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCompletionResilienceTest {

    private static final String REQUEST_ID = "req-1";
    private static final String KEY_HASH = "key-hash";
    private static final String REQUEST_BODY =
            "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_BODY =
            "{\"model\":\"upstream\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_RESPONSE =
            "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"upstream\","
                    + "\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,"
                    + "\"total_tokens\":30}}";
    private static final String FINAL_RESPONSE =
            "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"chat\",\"choices\":[]}";

    private ModelCatalog modelCatalog;
    private DeploymentHealthStore healthStore;
    private DeploymentSelector selector;
    private UpstreamClient upstreamClient;
    private OpenAiProtocolAdapter protocolAdapter;
    private GatewayProperties properties;
    private ChatCompletionCore core;

    @BeforeEach
    void setUp() {
        modelCatalog = mock(ModelCatalog.class);
        healthStore = mock(DeploymentHealthStore.class);
        selector = mock(DeploymentSelector.class);
        upstreamClient = mock(UpstreamClient.class);
        protocolAdapter = mock(OpenAiProtocolAdapter.class);
        properties = new GatewayProperties();
        core = new ChatCompletionCore(selector, upstreamClient, protocolAdapter,
                modelCatalog, healthStore, properties);

        when(modelCatalog.findFallbacks("chat")).thenReturn(List.of());
        when(protocolAdapter.rewriteRequestModel(anyString(), anyString())).thenReturn(UPSTREAM_BODY);
        when(protocolAdapter.extractUsage(anyString())).thenReturn(new TokenUsage(10, 20, 30));
        when(protocolAdapter.rewriteResponseModel(anyString(), eq("chat"))).thenReturn(FINAL_RESPONSE);
    }

    @Test
    void retries_same_deployment_then_succeeds() {
        ModelDeployment primary = deployment("chat", "upstream-primary", 0);
        properties.getResilience().setNumRetries(1);
        when(selector.select(eq("chat"), any())).thenReturn(primary);
        when(upstreamClient.chat(primary, UPSTREAM_BODY))
                .thenThrow(ChatCompletionException.upstreamTimeout(new RuntimeException("timeout")))
                .thenReturn(UPSTREAM_RESPONSE);

        ChatCompletionResultBO result = core.chat(bo("chat", List.of(primary)));

        assertThat(result.getResponseBody()).isEqualTo(FINAL_RESPONSE);
        assertThat(result.getServedAlias()).isEqualTo("chat");
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.isFallbackUsed()).isFalse();
        verify(upstreamClient, times(2)).chat(primary, UPSTREAM_BODY);
        verify(healthStore).recordFailure(primary);
        verify(healthStore).recordSuccess(primary);
    }

    @Test
    void switches_to_backup_deployment_within_alias() {
        ModelDeployment primary = deployment("chat", "upstream-primary", 0);
        ModelDeployment backup = deployment("chat", "upstream-backup", 1);
        properties.getResilience().setNumRetries(1);
        when(selector.select(eq("chat"), eq(List.of(primary)))).thenReturn(primary);
        when(selector.select(eq("chat"), eq(List.of(backup)))).thenReturn(backup);
        when(upstreamClient.chat(primary, UPSTREAM_BODY))
                .thenThrow(ChatCompletionException.upstreamError("boom", null));
        when(upstreamClient.chat(backup, UPSTREAM_BODY)).thenReturn(UPSTREAM_RESPONSE);

        ChatCompletionResultBO result = core.chat(bo("chat", List.of(primary, backup)));

        assertThat(result.getServedAlias()).isEqualTo("chat");
        assertThat(result.getDeploymentModel()).isEqualTo("upstream-backup");
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.isFallbackUsed()).isFalse();
        verify(upstreamClient).chat(primary, UPSTREAM_BODY);
        verify(upstreamClient).chat(backup, UPSTREAM_BODY);
    }

    @Test
    void falls_back_to_configured_alias() {
        ModelDeployment primary = deployment("chat", "upstream-primary", 0);
        ModelDeployment backup = deployment("chat-backup", "upstream-backup", 0);
        when(modelCatalog.findFallbacks("chat")).thenReturn(List.of("chat-backup"));
        when(modelCatalog.findDeployments("chat-backup")).thenReturn(List.of(backup));
        when(selector.select(eq("chat"), any())).thenReturn(primary);
        when(selector.select(eq("chat-backup"), any())).thenReturn(backup);
        when(upstreamClient.chat(primary, UPSTREAM_BODY))
                .thenThrow(ChatCompletionException.upstreamAuthError(new RuntimeException("bad key")));
        when(upstreamClient.chat(backup, UPSTREAM_BODY)).thenReturn(UPSTREAM_RESPONSE);

        ChatCompletionResultBO result = core.chat(bo("chat", List.of(primary)));

        assertThat(result.getServedAlias()).isEqualTo("chat-backup");
        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getDeploymentModel()).isEqualTo("upstream-backup");
        verify(protocolAdapter).rewriteResponseModel(UPSTREAM_RESPONSE, "chat");
    }

    @Test
    void does_not_retry_non_retryable_error() {
        ModelDeployment primary = deployment("chat", "upstream-primary", 0);
        properties.getResilience().setNumRetries(2);
        when(selector.select(eq("chat"), any())).thenReturn(primary);
        when(upstreamClient.chat(primary, UPSTREAM_BODY))
                .thenThrow(ChatCompletionException.upstreamClientError(400, null));

        assertThatThrownBy(() -> core.chat(bo("chat", List.of(primary))))
                .isInstanceOf(ChatCompletionException.class);

        verify(upstreamClient, times(1)).chat(primary, UPSTREAM_BODY);
        verify(healthStore, never()).recordFailure(any());
    }

    @Test
    void skips_cooling_down_deployment() {
        ModelDeployment primary = deployment("chat", "upstream-primary", 0);
        ModelDeployment backup = deployment("chat", "upstream-backup", 1);
        when(healthStore.isCoolingDown(primary)).thenReturn(true);
        when(selector.select(eq("chat"), any())).thenReturn(backup);
        when(upstreamClient.chat(backup, UPSTREAM_BODY)).thenReturn(UPSTREAM_RESPONSE);

        ChatCompletionResultBO result = core.chat(bo("chat", List.of(primary, backup)));

        assertThat(result.getDeploymentModel()).isEqualTo("upstream-backup");
        verify(upstreamClient, never()).chat(eq(primary), anyString());
    }

    @Test
    void tries_deployment_even_when_all_are_cooling_down() {
        ModelDeployment primary = deployment("chat", "upstream-primary", 0);
        when(healthStore.isCoolingDown(primary)).thenReturn(true);
        when(selector.select(eq("chat"), any())).thenReturn(primary);
        when(upstreamClient.chat(primary, UPSTREAM_BODY)).thenReturn(UPSTREAM_RESPONSE);

        ChatCompletionResultBO result = core.chat(bo("chat", List.of(primary)));

        assertThat(result.getServedAlias()).isEqualTo("chat");
        verify(upstreamClient).chat(primary, UPSTREAM_BODY);
    }

    @Test
    void stops_after_configured_retries() {
        ModelDeployment primary = deployment("chat", "upstream-primary", 0);
        properties.getResilience().setNumRetries(1);
        when(selector.select(eq("chat"), any())).thenReturn(primary);
        when(upstreamClient.chat(primary, UPSTREAM_BODY))
                .thenThrow(ChatCompletionException.upstreamTimeout(new RuntimeException("timeout")));

        assertThatThrownBy(() -> core.chat(bo("chat", List.of(primary))))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("timeout_error"));

        verify(upstreamClient, times(2)).chat(primary, UPSTREAM_BODY);
        verify(healthStore, times(2)).recordFailure(primary);
    }

    private ChatCompletionBO bo(String alias, List<ModelDeployment> deployments) {
        return new ChatCompletionBO(alias, deployments, REQUEST_BODY, REQUEST_ID, KEY_HASH);
    }

    private ModelDeployment deployment(String alias, String upstreamModel, int priority) {
        return new ModelDeployment(alias, "https://example.com", "test-key",
                upstreamModel, 1, priority);
    }
}
