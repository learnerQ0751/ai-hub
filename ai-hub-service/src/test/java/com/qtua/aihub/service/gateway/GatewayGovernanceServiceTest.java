package com.qtua.aihub.service.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.RateLimitProperties;
import com.qtua.aihub.common.constant.CallTypes;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenEstimate;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.pricing.ModelPrice;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.CallLogManager;
import com.qtua.aihub.manager.model.CallLogBO;
import com.qtua.aihub.service.gateway.model.GatewayContext;
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

class GatewayGovernanceServiceTest {

    private static final String KEY_HASH = "hash";
    private static final String REQUEST_ID = "req-1";
    private static final String BODY =
            "{\"model\":\"chat-default\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
    private static final BigDecimal RESERVED_COST = new BigDecimal("0.00011135");
    private static final BigDecimal ACTUAL_COST = new BigDecimal("0.00002470");

    private ModelCatalog modelCatalog;
    private GatewayGuard gatewayGuard;
    private PricingCatalog pricingCatalog;
    private CallLogManager callLogManager;
    private OpenAiProtocolAdapter protocolAdapter;
    private GatewayGovernanceService service;
    private GuardReservation reservation;

    @BeforeEach
    void setUp() {
        modelCatalog = mock(ModelCatalog.class);
        gatewayGuard = mock(GatewayGuard.class);
        pricingCatalog = mock(PricingCatalog.class);
        callLogManager = mock(CallLogManager.class);
        protocolAdapter = mock(OpenAiProtocolAdapter.class);
        service = new GatewayGovernanceService(modelCatalog, gatewayGuard, pricingCatalog,
                callLogManager, protocolAdapter, new RateLimitProperties(), new ObjectMapper());
        reservation = new GuardReservation(REQUEST_ID, KEY_HASH, "chat-default", "daily",
                105, RESERVED_COST, true, true);

        ModelDeployment deployment = new ModelDeployment("chat-default", "https://example.com",
                "key", "deepseek-flash", 1, 0);
        when(modelCatalog.findDeployments("chat-default")).thenReturn(List.of(deployment));
        when(protocolAdapter.estimateTokens(eq(BODY), anyInt()))
                .thenReturn(TokenEstimate.of(5, 100));
        when(pricingCatalog.findPrice("deepseek-flash")).thenReturn(Optional.of(
                new ModelPrice(new BigDecimal("0.27"), new BigDecimal("1.10"))));
        when(gatewayGuard.reserve(any())).thenReturn(reservation);
    }

    @Test
    void reserve_builds_request_and_reservation() {
        GatewayContext context = context(virtualKey());

        service.reserve(context, BODY);

        assertThat(context.getModelAlias()).isEqualTo("chat-default");
        assertThat(context.getEstimatedTokens()).isEqualTo(105);
        assertThat(context.getEstimatedCost()).isEqualByComparingTo(RESERVED_COST);
        assertThat(context.getReservation()).isEqualTo(reservation);
        ArgumentCaptor<GuardRequest> captor = ArgumentCaptor.forClass(GuardRequest.class);
        verify(gatewayGuard).reserve(captor.capture());
        GuardRequest request = captor.getValue();
        assertThat(request.requestId()).isEqualTo(REQUEST_ID);
        assertThat(request.keyHash()).isEqualTo(KEY_HASH);
        assertThat(request.rpmLimit()).isEqualTo(60);
        assertThat(request.tpmLimit()).isEqualTo(1000);
        assertThat(request.maxBudget()).isEqualByComparingTo("10");
        assertThat(request.budgetDuration()).isEqualTo("daily");
    }

    @Test
    void reserve_denies_model_outside_whitelist() {
        GatewayContext context = context(KeyAuthDTO.virtualKey(KEY_HASH, List.of("other"),
                60, 1000, BigDecimal.TEN, "daily"));

        assertThatThrownBy(() -> service.reserve(context, BODY))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("key_model_access_denied"));
        verifyNoInteractions(gatewayGuard);
    }

    @Test
    void reserve_rejects_unknown_model() {
        when(modelCatalog.findDeployments("chat-default")).thenReturn(List.of());
        GatewayContext context = context(virtualKey());

        assertThatThrownBy(() -> service.reserve(context, BODY))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getParam())
                        .isEqualTo("model"));
        verifyNoInteractions(gatewayGuard);
    }

    @Test
    void reserve_ignores_invalid_json() {
        GatewayContext context = context(virtualKey());

        service.reserve(context, "not-json");

        assertThat(context.hasModelAlias()).isFalse();
        verifyNoInteractions(gatewayGuard);
    }

    @Test
    void complete_settles_and_logs_success() {
        GatewayContext context = context(virtualKey());
        context.setModelAlias("chat-default");
        context.setReservation(reservation);
        context.complete(new TokenUsage(10, 20, 30), "deepseek-flash",
                "chat-backup", 1, true);

        service.complete(context);

        verify(gatewayGuard).settle(reservation, new TokenUsage(10, 20, 30), ACTUAL_COST);
        verify(callLogManager).addSpend(KEY_HASH, ACTUAL_COST);
        CallLogBO log = capturedLog();
        assertThat(log.status()).isEqualTo(200);
        assertThat(log.errorType()).isNull();
        assertThat(log.deploymentModel()).isEqualTo("deepseek-flash");
        assertThat(log.cost()).isEqualByComparingTo(ACTUAL_COST);
        assertThat(log.servedAlias()).isEqualTo("chat-backup");
        assertThat(log.retryCount()).isEqualTo(1);
        assertThat(log.fallbackUsed()).isTrue();
    }

    @Test
    void fail_releases_and_logs_failure() {
        GatewayContext context = context(virtualKey());
        context.setModelAlias("chat-default");
        context.setReservation(reservation);
        context.fail(502, "upstream_error");

        service.fail(context);

        verify(gatewayGuard).release(reservation);
        CallLogBO log = capturedLog();
        assertThat(log.status()).isEqualTo(502);
        assertThat(log.errorType()).isEqualTo("upstream_error");
        assertThat(log.cost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fail_without_alias_skips_log() {
        GatewayContext context = context(virtualKey());
        context.fail(400, "invalid_request_error");

        service.fail(context);

        verifyNoInteractions(callLogManager);
    }

    @Test
    void complete_without_alias_skips() {
        GatewayContext context = context(virtualKey());

        service.complete(context);

        verifyNoInteractions(gatewayGuard, callLogManager);
    }

    @Test
    void reserve_memory_call_uses_only_rpm() {
        GatewayContext context = context(virtualKey());
        context.setCallTarget(CallTypes.MEMORY, "save");

        service.reserve(context, "{\"tenant_id\":\"t1\"}");

        assertThat(context.getModelAlias()).isEqualTo("save");
        ArgumentCaptor<GuardRequest> captor = ArgumentCaptor.forClass(GuardRequest.class);
        verify(gatewayGuard).reserve(captor.capture());
        GuardRequest request = captor.getValue();
        assertThat(request.modelAlias()).isEqualTo("save");
        assertThat(request.rpmLimit()).isEqualTo(60);
        assertThat(request.tpmLimit()).isNull();
        assertThat(request.maxBudget()).isNull();
        assertThat(request.estimatedTokens()).isZero();
    }

    @Test
    void complete_memory_call_settles_and_logs_with_call_type() {
        GatewayContext context = context(virtualKey());
        context.setCallTarget(CallTypes.MEMORY, "recall");
        context.setModelAlias("recall");
        context.setReservation(reservation);

        service.complete(context);

        verify(gatewayGuard).settle(reservation, TokenUsage.ZERO, BigDecimal.ZERO);
        CallLogBO log = capturedLog();
        assertThat(log.callType()).isEqualTo("MEMORY");
        assertThat(log.targetName()).isEqualTo("recall");
        assertThat(log.cost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(log.status()).isEqualTo(200);
    }

    @Test
    void fail_memory_call_releases_and_logs() {
        GatewayContext context = context(virtualKey());
        context.setCallTarget(CallTypes.MEMORY, "clear");
        context.setModelAlias("clear");
        context.setReservation(reservation);
        context.fail(400, "invalid_request_error");

        service.fail(context);

        verify(gatewayGuard).release(reservation);
        CallLogBO log = capturedLog();
        assertThat(log.callType()).isEqualTo("MEMORY");
        assertThat(log.status()).isEqualTo(400);
    }

    @Test
    void reserve_rag_call_uses_only_rpm_and_no_body_parse() {
        GatewayContext context = context(virtualKey());
        context.setCallTarget(CallTypes.RAG, "ingest_document");

        service.reserve(context, null);

        assertThat(context.getModelAlias()).isEqualTo("ingest_document");
        ArgumentCaptor<GuardRequest> captor = ArgumentCaptor.forClass(GuardRequest.class);
        verify(gatewayGuard).reserve(captor.capture());
        GuardRequest request = captor.getValue();
        assertThat(request.modelAlias()).isEqualTo("ingest_document");
        assertThat(request.tpmLimit()).isNull();
        assertThat(request.estimatedTokens()).isZero();
    }

    @Test
    void complete_rag_call_logs_call_type_and_zero_cost() {
        GatewayContext context = context(virtualKey());
        context.setCallTarget(CallTypes.RAG, "reindex_document");
        context.setModelAlias("reindex_document");
        context.setReservation(reservation);

        service.complete(context);

        verify(gatewayGuard).settle(reservation, TokenUsage.ZERO, BigDecimal.ZERO);
        CallLogBO log = capturedLog();
        assertThat(log.callType()).isEqualTo("RAG");
        assertThat(log.targetName()).isEqualTo("reindex_document");
        assertThat(log.cost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fail_rag_call_releases_and_logs() {
        GatewayContext context = context(virtualKey());
        context.setCallTarget(CallTypes.RAG, "delete_document");
        context.setModelAlias("delete_document");
        context.setReservation(reservation);
        context.fail(404, "not_found_error");

        service.fail(context);

        verify(gatewayGuard).release(reservation);
        CallLogBO log = capturedLog();
        assertThat(log.callType()).isEqualTo("RAG");
        assertThat(log.status()).isEqualTo(404);
    }

    private GatewayContext context(KeyAuthDTO auth) {
        return new GatewayContext(REQUEST_ID, auth.keyHash(),
                "/v1/chat/completions", auth);
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
