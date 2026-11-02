package com.qtua.aihub.core;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.RateLimitProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenEstimate;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.pricing.ModelPrice;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.integration.ratelimit.RedisRateLimiter;
import com.qtua.aihub.manager.CallLogManager;
import com.qtua.aihub.manager.model.CallLogBO;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatCompletionCoreTest {

    private static final String REQUEST_ID = "req-1";
    private static final String KEY_HASH = "key-hash";
    private static final String REQUEST_BODY =
            "{\"model\":\"chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_BODY =
            "{\"model\":\"deepseek-flash\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String UPSTREAM_RESPONSE =
            "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"deepseek-flash\","
                    + "\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,"
                    + "\"total_tokens\":30}}";
    private static final String FINAL_RESPONSE =
            "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"chat\",\"choices\":[]}";

    private ModelDeployment deployment;
    private DeploymentSelector selector;
    private UpstreamClient upstreamClient;
    private OpenAiProtocolAdapter protocolAdapter;
    private PricingCatalog pricingCatalog;
    private CallLogManager callLogManager;
    private RedisRateLimiter rateLimiter;
    private ChatCompletionCore core;

    @BeforeEach
    void setUp() {
        deployment = new ModelDeployment("chat", "https://example.com", "test-key",
                "deepseek-flash", 1);

        selector = mock(DeploymentSelector.class);
        upstreamClient = mock(UpstreamClient.class);
        protocolAdapter = mock(OpenAiProtocolAdapter.class);
        pricingCatalog = mock(PricingCatalog.class);
        callLogManager = mock(CallLogManager.class);
        rateLimiter = mock(RedisRateLimiter.class);
        RateLimitProperties properties = new RateLimitProperties();
        core = new ChatCompletionCore(selector, upstreamClient, protocolAdapter,
                pricingCatalog, callLogManager, rateLimiter, properties);

        when(selector.select(eq("chat"), eq(List.of(deployment)))).thenReturn(deployment);
        when(protocolAdapter.rewriteRequestModel(REQUEST_BODY, "deepseek-flash")).thenReturn(UPSTREAM_BODY);
        when(protocolAdapter.estimateTokens(eq(REQUEST_BODY), anyInt()))
                .thenReturn(TokenEstimate.of(5, 100));
        when(upstreamClient.chat(deployment, UPSTREAM_BODY)).thenReturn(UPSTREAM_RESPONSE);
        when(protocolAdapter.extractUsage(UPSTREAM_RESPONSE)).thenReturn(new TokenUsage(10, 20, 30));
        when(protocolAdapter.rewriteResponseModel(UPSTREAM_RESPONSE, "chat")).thenReturn(FINAL_RESPONSE);
        when(pricingCatalog.findPrice("deepseek-flash")).thenReturn(Optional.of(
                new ModelPrice(new BigDecimal("0.27"), new BigDecimal("1.10"))));
        // 默认放行；具体用例可用更精确的 stub 覆盖。
        when(rateLimiter.tryAcquireRpm(any(), any(), any(), any())).thenReturn(true);
        when(rateLimiter.tryAcquireTpm(any(), any(), any(), anyLong(), any())).thenReturn(true);
        when(rateLimiter.tryReserveBudget(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void orchestrates_routing_protocol_and_upstream_call() {
        ChatCompletionBO bo = masterBO("chat", List.of(deployment));

        ChatCompletionResultBO result = core.chat(bo);

        assertThat(result.getResponseBody()).isEqualTo(FINAL_RESPONSE);
        verify(selector).select(eq("chat"), eq(List.of(deployment)));
        verify(protocolAdapter).rewriteRequestModel(REQUEST_BODY, "deepseek-flash");
        verify(upstreamClient).chat(deployment, UPSTREAM_BODY);
        verify(protocolAdapter).rewriteResponseModel(UPSTREAM_RESPONSE, "chat");
    }

    @Test
    void allows_model_in_virtual_key_whitelist() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"));

        ChatCompletionResultBO result = core.chat(bo);

        assertThat(result.getResponseBody()).isEqualTo(FINAL_RESPONSE);
    }

    @Test
    void records_success_log_and_accumulates_spend() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"));

        core.chat(bo);

        CallLogBO log = capturedLog();
        assertThat(log.requestId()).isEqualTo(REQUEST_ID);
        assertThat(log.keyId()).isEqualTo(KEY_HASH);
        assertThat(log.modelAlias()).isEqualTo("chat");
        assertThat(log.deploymentModel()).isEqualTo("deepseek-flash");
        assertThat(log.usage()).isEqualTo(new TokenUsage(10, 20, 30));
        assertThat(log.status()).isEqualTo(200);
        assertThat(log.errorType()).isNull();
        // 10 * 0.27/1M + 20 * 1.10/1M = 0.00002470
        assertThat(log.cost()).isEqualByComparingTo("0.00002470");
        assertThat(log.latencyMs()).isGreaterThanOrEqualTo(0);
        verify(callLogManager).addSpend(KEY_HASH, log.cost());
    }

    @Test
    void records_zero_cost_when_price_is_missing() {
        when(pricingCatalog.findPrice("deepseek-flash")).thenReturn(Optional.empty());
        ChatCompletionBO bo = masterBO("chat", List.of(deployment));

        core.chat(bo);

        assertThat(capturedLog().cost()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(callLogManager).addSpend(null, BigDecimal.ZERO);
    }

    @Test
    void records_failure_log_when_model_is_denied() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("reasoner"));

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                    assertThat(exception.getType()).isEqualTo("key_model_access_denied");
                    assertThat(exception.getParam()).isEqualTo("model");
                    assertThat(exception.getCode()).isEqualTo("403");
                });

        CallLogBO log = capturedLog();
        assertThat(log.status()).isEqualTo(403);
        assertThat(log.errorType()).isEqualTo("key_model_access_denied");
        assertThat(log.deploymentModel()).isNull();
        assertThat(log.cost()).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(upstreamClient);
    }

    @Test
    void records_failure_log_when_model_is_unknown() {
        ChatCompletionBO bo = masterBO("no-such-model", List.of());

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getParam()).isEqualTo("model");
                });

        assertThat(capturedLog().status()).isEqualTo(400);
    }

    @Test
    void denies_before_unknown_model_for_virtual_key() {
        // 不在白名单时优先 403，避免通过 400/403 差异探测模型目录。
        ChatCompletionBO bo = virtualKeyBO("no-such-model", List.of(), List.of("chat"));

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("key_model_access_denied"));
        assertThat(capturedLog().status()).isEqualTo(403);
    }

    @Test
    void propagates_upstream_error_and_records_status() {
        when(upstreamClient.chat(eq(deployment), anyString()))
                .thenThrow(ChatCompletionException.upstreamTimeout(new RuntimeException("timeout")));
        ChatCompletionBO bo = masterBO("chat", List.of(deployment));

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("timeout_error"));

        CallLogBO log = capturedLog();
        assertThat(log.status()).isEqualTo(504);
        assertThat(log.deploymentModel()).isEqualTo("deepseek-flash");
    }

    @Test
    void propagates_protocol_error_and_records_status() {
        when(protocolAdapter.rewriteResponseModel(UPSTREAM_RESPONSE, "chat"))
                .thenThrow(ChatCompletionException.upstreamError("bad json", null));
        ChatCompletionBO bo = masterBO("chat", List.of(deployment));

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("upstream_error"));

        assertThat(capturedLog().status()).isEqualTo(502);
    }

    @Test
    void rpm_exceeded_returns_429_and_records_log() {
        when(rateLimiter.tryAcquireRpm(eq(KEY_HASH), eq("chat"), eq(REQUEST_ID), eq(1)))
                .thenReturn(false);
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"),
                1, null, null, null);

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value()).isEqualTo(429);
                    assertThat(exception.getType()).isEqualTo("rate_limit_error");
                });

        CallLogBO log = capturedLog();
        assertThat(log.status()).isEqualTo(429);
        assertThat(log.errorType()).isEqualTo("rate_limit_error");
        verifyNoInteractions(upstreamClient);
    }

    @Test
    void tpm_is_reserved_before_call_and_settled_with_actual_usage() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"),
                null, 1000, null, null);
        when(rateLimiter.tryAcquireTpm(KEY_HASH, "chat", REQUEST_ID, 105, 1000)).thenReturn(true);

        core.chat(bo);

        verify(rateLimiter).tryAcquireTpm(KEY_HASH, "chat", REQUEST_ID, 105, 1000);
        verify(rateLimiter).settleTpm(KEY_HASH, "chat", REQUEST_ID, 105, 30);
    }

    @Test
    void tpm_exceeded_returns_429_and_releases_nothing() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"),
                null, 1000, null, null);
        when(rateLimiter.tryAcquireTpm(KEY_HASH, "chat", REQUEST_ID, 105, 1000)).thenReturn(false);

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("rate_limit_error"));
        verify(rateLimiter, never()).settleTpm(any(), any(), any(), anyLong(), anyLong());
        verifyNoInteractions(upstreamClient);
    }

    @Test
    void budget_is_reserved_and_settled_with_actual_cost() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"),
                null, null, new BigDecimal("10"), "daily");
        when(rateLimiter.tryReserveBudget(eq(KEY_HASH), eq("daily"), eq(new BigDecimal("10")), any()))
                .thenReturn(true);

        core.chat(bo);

        ArgumentCaptor<BigDecimal> reserved = ArgumentCaptor.forClass(BigDecimal.class);
        verify(rateLimiter).tryReserveBudget(eq(KEY_HASH), eq("daily"),
                eq(new BigDecimal("10")), reserved.capture());
        assertThat(reserved.getValue()).isEqualByComparingTo("0.00011135");
        verify(rateLimiter).settleBudget(KEY_HASH, "daily",
                reserved.getValue(), new BigDecimal("0.00002470"));
    }

    @Test
    void budget_exceeded_returns_400() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"),
                null, null, new BigDecimal("0.00001"), "daily");
        when(rateLimiter.tryReserveBudget(eq(KEY_HASH), eq("daily"),
                eq(new BigDecimal("0.00001")), any())).thenReturn(false);

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getType()).isEqualTo("budget_exceeded");
                });
        verifyNoInteractions(upstreamClient);
    }

    @Test
    void failure_after_reservation_releases_tpm_and_budget() {
        ChatCompletionBO bo = virtualKeyBO("chat", List.of(deployment), List.of("chat"),
                null, 1000, new BigDecimal("10"), "daily");
        when(rateLimiter.tryAcquireTpm(KEY_HASH, "chat", REQUEST_ID, 105, 1000)).thenReturn(true);
        when(rateLimiter.tryReserveBudget(eq(KEY_HASH), eq("daily"), eq(new BigDecimal("10")), any()))
                .thenReturn(true);
        when(upstreamClient.chat(eq(deployment), anyString()))
                .thenThrow(ChatCompletionException.upstreamTimeout(new RuntimeException("timeout")));

        assertThatThrownBy(() -> core.chat(bo))
                .isInstanceOf(ChatCompletionException.class);

        verify(rateLimiter).settleTpm(KEY_HASH, "chat", REQUEST_ID, 105, 0);
        ArgumentCaptor<BigDecimal> reserved = ArgumentCaptor.forClass(BigDecimal.class);
        verify(rateLimiter).releaseBudget(eq(KEY_HASH), eq("daily"), reserved.capture());
        assertThat(reserved.getValue()).isEqualByComparingTo("0.00011135");
    }

    private CallLogBO capturedLog() {
        ArgumentCaptor<CallLogBO> captor = ArgumentCaptor.forClass(CallLogBO.class);
        verify(callLogManager).record(captor.capture());
        return captor.getValue();
    }

    private ChatCompletionBO masterBO(String alias, List<ModelDeployment> deployments) {
        return new ChatCompletionBO(alias, deployments, REQUEST_BODY, List.of(), true,
                REQUEST_ID, null, null, null, null, null);
    }

    private ChatCompletionBO virtualKeyBO(String alias, List<ModelDeployment> deployments,
                                         List<String> allowedModels) {
        return virtualKeyBO(alias, deployments, allowedModels, null, null, null, null);
    }

    private ChatCompletionBO virtualKeyBO(String alias, List<ModelDeployment> deployments,
                                         List<String> allowedModels, Integer rpmLimit,
                                         Integer tpmLimit, BigDecimal maxBudget,
                                         String budgetDuration) {
        return new ChatCompletionBO(alias, deployments, REQUEST_BODY, allowedModels, false,
                REQUEST_ID, KEY_HASH, rpmLimit, tpmLimit, maxBudget, budgetDuration);
    }
}
