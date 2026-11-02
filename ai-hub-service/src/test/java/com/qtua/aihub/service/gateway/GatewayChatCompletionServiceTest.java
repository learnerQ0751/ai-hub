package com.qtua.aihub.service.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.RateLimitProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenEstimate;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.pricing.ModelPrice;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.CallLogManager;
import com.qtua.aihub.manager.model.CallLogBO;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import com.qtua.aihub.service.gateway.model.GuardRequest;
import com.qtua.aihub.service.gateway.model.GuardReservation;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GatewayChatCompletionServiceTest {

    private static final String KEY_HASH = "hash";
    private static final String REQUEST_ID = "req-1";
    private static final String BODY =
            "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final String RESPONSE = "{\"object\":\"chat.completion\"}";
    private static final BigDecimal RESERVED_COST = new BigDecimal("0.00011135");
    private static final BigDecimal ACTUAL_COST = new BigDecimal("0.00002470");

    private ModelCatalog modelCatalog;
    private ChatExecutor chatExecutor;
    private GatewayGuard gatewayGuard;
    private PricingCatalog pricingCatalog;
    private CallLogManager callLogManager;
    private OpenAiProtocolAdapter protocolAdapter;
    private GatewayChatCompletionService service;
    private ModelDeployment deployment;
    private GuardReservation reservation;

    @BeforeEach
    void setUp() {
        modelCatalog = mock(ModelCatalog.class);
        chatExecutor = mock(ChatExecutor.class);
        gatewayGuard = mock(GatewayGuard.class);
        pricingCatalog = mock(PricingCatalog.class);
        callLogManager = mock(CallLogManager.class);
        protocolAdapter = mock(OpenAiProtocolAdapter.class);
        deployment = new ModelDeployment("chat-default", "https://example.com", "key",
                "deepseek-flash", 1);
        reservation = new GuardReservation(REQUEST_ID, KEY_HASH, "chat-default", "daily",
                105, RESERVED_COST, true, true);
        service = new GatewayChatCompletionService(modelCatalog, chatExecutor, gatewayGuard,
                pricingCatalog, callLogManager, protocolAdapter, new RateLimitProperties(),
                new ObjectMapper());

        when(modelCatalog.findDeployments("chat-default")).thenReturn(List.of(deployment));
        when(protocolAdapter.estimateTokens(eq(BODY), anyInt()))
                .thenReturn(TokenEstimate.of(5, 100));
        when(pricingCatalog.findPrice("deepseek-flash")).thenReturn(Optional.of(
                new ModelPrice(new BigDecimal("0.27"), new BigDecimal("1.10"))));
        when(gatewayGuard.reserve(any())).thenReturn(reservation);
        when(chatExecutor.execute(any())).thenReturn(new ChatCompletionResultBO(
                RESPONSE, new TokenUsage(10, 20, 30), "deepseek-flash"));
    }

    @Test
    void reserves_executes_settles_and_logs() {
        ChatCompletionResDTO response = service.chat(
                new ChatCompletionReqDTO(BODY, virtualKey(), REQUEST_ID));

        assertThat(response.getRawResponse()).isEqualTo(RESPONSE);
        ArgumentCaptor<GuardRequest> guardCaptor = ArgumentCaptor.forClass(GuardRequest.class);
        verify(gatewayGuard).reserve(guardCaptor.capture());
        GuardRequest guardRequest = guardCaptor.getValue();
        assertThat(guardRequest.requestId()).isEqualTo(REQUEST_ID);
        assertThat(guardRequest.keyHash()).isEqualTo(KEY_HASH);
        assertThat(guardRequest.rpmLimit()).isEqualTo(60);
        assertThat(guardRequest.tpmLimit()).isEqualTo(1000);
        assertThat(guardRequest.maxBudget()).isEqualByComparingTo("10");
        assertThat(guardRequest.budgetDuration()).isEqualTo("daily");
        assertThat(guardRequest.estimatedTokens()).isEqualTo(105);
        assertThat(guardRequest.estimatedCost()).isEqualByComparingTo(RESERVED_COST);

        ArgumentCaptor<ChatCompletionBO> boCaptor = ArgumentCaptor.forClass(ChatCompletionBO.class);
        verify(chatExecutor).execute(boCaptor.capture());
        assertThat(boCaptor.getValue().getAlias()).isEqualTo("chat-default");
        verify(gatewayGuard).settle(reservation, new TokenUsage(10, 20, 30), ACTUAL_COST);
        verify(callLogManager).addSpend(KEY_HASH, ACTUAL_COST);
        CallLogBO log = capturedLog();
        assertThat(log.requestId()).isEqualTo(REQUEST_ID);
        assertThat(log.keyId()).isEqualTo(KEY_HASH);
        assertThat(log.deploymentModel()).isEqualTo("deepseek-flash");
        assertThat(log.status()).isEqualTo(200);
        assertThat(log.errorType()).isNull();
    }

    @Test
    void master_key_passes_empty_limits() {
        service.chat(new ChatCompletionReqDTO(BODY, KeyAuthDTO.master(), REQUEST_ID));

        ArgumentCaptor<GuardRequest> captor = ArgumentCaptor.forClass(GuardRequest.class);
        verify(gatewayGuard).reserve(captor.capture());
        assertThat(captor.getValue().keyHash()).isNull();
        assertThat(captor.getValue().rpmLimit()).isNull();
        assertThat(capturedLog().keyId()).isNull();
    }

    @Test
    void denies_model_outside_whitelist() {
        KeyAuthDTO auth = KeyAuthDTO.virtualKey(KEY_HASH, List.of("other"),
                60, 1000, BigDecimal.TEN, "daily");

        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(BODY, auth, REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                    assertThat(exception.getType()).isEqualTo("key_model_access_denied");
                });
        verifyNoInteractions(gatewayGuard, chatExecutor);
    }

    @Test
    void rejects_unknown_model() {
        when(modelCatalog.findDeployments("chat-default")).thenReturn(List.of());

        assertThatThrownBy(() -> service.chat(
                new ChatCompletionReqDTO(BODY, KeyAuthDTO.master(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("model"));
        verifyNoInteractions(gatewayGuard, chatExecutor);
    }

    @Test
    void rpm_exceeded_returns_429_and_logs() {
        when(gatewayGuard.reserve(any()))
                .thenThrow(ChatCompletionException.rateLimitExceeded("requests per minute limit"));

        assertThatThrownBy(() -> service.chat(
                new ChatCompletionReqDTO(BODY, virtualKey(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value()).isEqualTo(429);
                    assertThat(exception.getType()).isEqualTo("rate_limit_error");
                });
        verifyNoInteractions(chatExecutor);
        CallLogBO log = capturedLog();
        assertThat(log.status()).isEqualTo(429);
        assertThat(log.errorType()).isEqualTo("rate_limit_error");
    }

    @Test
    void budget_exceeded_returns_400_and_logs() {
        when(gatewayGuard.reserve(any()))
                .thenThrow(ChatCompletionException.budgetExceeded());

        assertThatThrownBy(() -> service.chat(
                new ChatCompletionReqDTO(BODY, virtualKey(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException exception = (ChatCompletionException) ex;
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getType()).isEqualTo("budget_exceeded");
                });
        assertThat(capturedLog().status()).isEqualTo(400);
    }

    @Test
    void executor_failure_releases_reservation_and_logs() {
        when(chatExecutor.execute(any()))
                .thenThrow(ChatCompletionException.upstreamTimeout(new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.chat(
                new ChatCompletionReqDTO(BODY, virtualKey(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class);
        verify(gatewayGuard).release(reservation);
        assertThat(capturedLog().status()).isEqualTo(504);
    }

    @Test
    void rejects_streaming() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"model\":\"chat-default\",\"stream\":true,"
                        + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                KeyAuthDTO.master(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("stream"));
    }

    @Test
    void rejects_empty_messages() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"model\":\"chat-default\",\"messages\":[]}", KeyAuthDTO.master(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("messages"));
    }

    @Test
    void rejects_invalid_json() {
        assertThatThrownBy(() -> service.chat(
                new ChatCompletionReqDTO("not-json", KeyAuthDTO.master(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("invalid_request_error"));
    }

    @Test
    void rejects_missing_model() {
        assertThatThrownBy(() -> service.chat(new ChatCompletionReqDTO(
                "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                KeyAuthDTO.master(), REQUEST_ID)))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("model"));
    }

    private KeyAuthDTO virtualKey() {
        return KeyAuthDTO.virtualKey(KEY_HASH, List.of("chat-default"),
                60, 1000, BigDecimal.TEN, "daily");
    }

    private CallLogBO capturedLog() {
        ArgumentCaptor<CallLogBO> captor = ArgumentCaptor.forClass(CallLogBO.class);
        verify(callLogManager).record(captor.capture());
        return captor.getValue();
    }
}
