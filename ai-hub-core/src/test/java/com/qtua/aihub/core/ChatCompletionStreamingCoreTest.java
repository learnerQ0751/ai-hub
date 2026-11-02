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
import com.qtua.aihub.manager.model.ChatCompletionStreamResultBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatCompletionStreamingCoreTest {

    private static final String REQUEST_ID = "req-1";
    private static final String KEY_HASH = "key-hash";
    private static final String REQUEST_BODY =
            "{\"model\":\"chat\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_BODY =
            "{\"model\":\"upstream\",\"stream\":true,\"messages\":[]}";
    private static final String CHUNK_1 = "{\"model\":\"upstream\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}";
    private static final String CHUNK_2 = "{\"model\":\"upstream\",\"choices\":[],"
            + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";
    private static final String REWRITTEN_1 = "{\"model\":\"chat\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}";
    private static final String REWRITTEN_2 = "{\"model\":\"chat\",\"choices\":[],"
            + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";

    private ModelCatalog modelCatalog;
    private DeploymentHealthStore healthStore;
    private DeploymentSelector selector;
    private UpstreamClient upstreamClient;
    private OpenAiProtocolAdapter protocolAdapter;
    private ChatCompletionCore core;

    @BeforeEach
    void setUp() {
        modelCatalog = mock(ModelCatalog.class);
        healthStore = mock(DeploymentHealthStore.class);
        selector = mock(DeploymentSelector.class);
        upstreamClient = mock(UpstreamClient.class);
        protocolAdapter = mock(OpenAiProtocolAdapter.class);
        core = new ChatCompletionCore(selector, upstreamClient, protocolAdapter,
                modelCatalog, healthStore, new GatewayProperties());
        when(modelCatalog.findFallbacks("chat")).thenReturn(List.of());
    }

    @Test
    void streams_events_and_accumulates_usage() {
        ModelDeployment deployment = deployment("upstream");
        when(selector.select(eq("chat"), any())).thenReturn(deployment);
        when(protocolAdapter.rewriteStreamRequest(REQUEST_BODY, "upstream")).thenReturn(UPSTREAM_BODY);
        when(upstreamClient.stream(deployment, UPSTREAM_BODY))
                .thenReturn(Flux.just(CHUNK_1, CHUNK_2, "[DONE]"));
        when(protocolAdapter.extractUsage(CHUNK_1)).thenReturn(TokenUsage.ZERO);
        when(protocolAdapter.extractUsage(CHUNK_2)).thenReturn(new TokenUsage(10, 20, 30));
        when(protocolAdapter.rewriteStreamResponseModel(CHUNK_1, "chat")).thenReturn(REWRITTEN_1);
        when(protocolAdapter.rewriteStreamResponseModel(CHUNK_2, "chat")).thenReturn(REWRITTEN_2);
        when(protocolAdapter.rewriteStreamResponseModel("[DONE]", "chat")).thenReturn("[DONE]");

        ChatCompletionStreamResultBO result = core.chatStream(bo());

        List<String> events = result.getEvents().collectList().block(Duration.ofSeconds(5));
        assertThat(events).containsExactly(REWRITTEN_1, REWRITTEN_2, "[DONE]");
        assertThat(result.getUsage().get()).isEqualTo(new TokenUsage(10, 20, 30));
        assertThat(result.getServedAlias()).isEqualTo("chat");
        assertThat(result.getDeploymentModel()).isEqualTo("upstream");
    }

    @Test
    void skips_cooling_down_deployment_for_stream() {
        ModelDeployment primary = deployment("primary");
        ModelDeployment backup = deployment("backup");
        when(healthStore.isCoolingDown(primary)).thenReturn(true);
        when(selector.select(eq("chat"), any())).thenReturn(backup);
        when(protocolAdapter.rewriteStreamRequest(REQUEST_BODY, "backup")).thenReturn(UPSTREAM_BODY);
        when(upstreamClient.stream(backup, UPSTREAM_BODY)).thenReturn(Flux.just("[DONE]"));
        when(protocolAdapter.rewriteStreamResponseModel("[DONE]", "chat")).thenReturn("[DONE]");

        ChatCompletionStreamResultBO result = core.chatStream(
                new ChatCompletionBO("chat", List.of(primary, backup), REQUEST_BODY,
                        REQUEST_ID, KEY_HASH));

        assertThat(result.getDeploymentModel()).isEqualTo("backup");
    }

    @Test
    void rejects_unknown_model_for_stream() {
        assertThatThrownBy(() -> core.chatStream(
                new ChatCompletionBO("no-such-model", List.of(), REQUEST_BODY,
                        REQUEST_ID, KEY_HASH)))
                .isInstanceOf(ChatCompletionException.class);
    }

    private ChatCompletionBO bo() {
        return new ChatCompletionBO("chat", List.of(deployment("upstream")),
                REQUEST_BODY, REQUEST_ID, KEY_HASH);
    }

    private ModelDeployment deployment(String upstreamModel) {
        return new ModelDeployment("chat", "https://example.com", "test-key",
                upstreamModel, 1, 0);
    }
}
