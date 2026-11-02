package com.qtua.aihub.manager;

import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.dal.mapper.ApiKeyMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import com.qtua.aihub.manager.model.CallLogBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CallLogManagerTest {

    private static final String KEY_ID = "key-hash";

    private CallLogMapper callLogMapper;
    private ApiKeyMapper apiKeyMapper;
    private CallLogManager manager;

    @BeforeEach
    void setUp() {
        callLogMapper = mock(CallLogMapper.class);
        apiKeyMapper = mock(ApiKeyMapper.class);
        Executor directExecutor = Runnable::run;
        manager = new CallLogManager(callLogMapper, apiKeyMapper, directExecutor);
    }

    @Test
    void persists_all_call_log_fields() {
        CallLogBO callLog = callLog(new BigDecimal("0.00001234"), 200);

        manager.record(callLog);

        ArgumentCaptor<CallLogDO> captor = ArgumentCaptor.forClass(CallLogDO.class);
        verify(callLogMapper).insert(captor.capture());
        CallLogDO stored = captor.getValue();
        assertThat(stored.getRequestId()).isEqualTo("req-1");
        assertThat(stored.getKeyId()).isEqualTo(KEY_ID);
        assertThat(stored.getModelAlias()).isEqualTo("chat");
        assertThat(stored.getDeploymentModel()).isEqualTo("deepseek-chat");
        assertThat(stored.getPromptTokens()).isEqualTo(10);
        assertThat(stored.getCompletionTokens()).isEqualTo(20);
        assertThat(stored.getTotalTokens()).isEqualTo(30);
        assertThat(stored.getCost()).isEqualByComparingTo("0.00001234");
        assertThat(stored.getLatencyMs()).isEqualTo(100L);
        assertThat(stored.getStatus()).isEqualTo(200);
        assertThat(stored.getCreatedAt()).isEqualTo(callLog.createdAt());
    }

    @Test
    void swallows_persistence_failure() {
        doThrow(new IllegalStateException("db down")).when(callLogMapper).insert(any());

        manager.record(callLog(BigDecimal.ZERO, 500));

        verify(callLogMapper).insert(any());
    }

    @Test
    void falls_back_to_caller_when_executor_rejects() {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("full");
        };
        CallLogManager rejectingManager = new CallLogManager(callLogMapper, apiKeyMapper, rejecting);

        rejectingManager.record(callLog(BigDecimal.ZERO, 200));

        verify(callLogMapper).insert(any());
    }

    @Test
    void accumulates_spend_for_virtual_key() {
        manager.addSpend(KEY_ID, new BigDecimal("0.01"));

        verify(apiKeyMapper).addSpend(KEY_ID, new BigDecimal("0.01"));
    }

    @Test
    void skips_spend_for_master_or_zero_cost() {
        manager.addSpend(null, new BigDecimal("0.01"));
        manager.addSpend(KEY_ID, BigDecimal.ZERO);
        manager.addSpend(KEY_ID, null);

        verifyNoInteractions(apiKeyMapper);
    }

    @Test
    void swallows_spend_failure() {
        doThrow(new IllegalStateException("db down")).when(apiKeyMapper).addSpend(any(), any());

        manager.addSpend(KEY_ID, new BigDecimal("0.01"));
    }

    private CallLogBO callLog(BigDecimal cost, int status) {
        return new CallLogBO("req-1", KEY_ID, "chat", "deepseek-chat",
                new TokenUsage(10, 20, 30), cost, 100L, status, null,
                Instant.parse("2026-11-02T00:00:00Z"));
    }
}
