package com.qtua.aihub.integration.ratelimit;

import com.qtua.aihub.common.config.RateLimitProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RedisRateLimiterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

    private StringRedisTemplate redisTemplate;
    private RateLimitProperties properties;
    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        properties = new RateLimitProperties();
        limiter = new RedisRateLimiter(redisTemplate, properties, CLOCK);
    }

    @Test
    void rpm_without_limit_passes_without_redis() {
        assertThat(limiter.tryAcquireRpm("key", "chat", "req", null)).isTrue();
        assertThat(limiter.tryAcquireRpm("key", "chat", "req", 0)).isTrue();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rpm_allows_when_script_returns_one() {
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThat(limiter.tryAcquireRpm("key", "chat", "req", 2)).isTrue();
    }

    @Test
    void rpm_denies_when_script_returns_zero() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThat(limiter.tryAcquireRpm("key", "chat", "req", 2)).isFalse();
    }

    @Test
    void fail_open_allows_when_redis_is_unavailable() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThat(limiter.tryAcquireRpm("key", "chat", "req", 2)).isTrue();
    }

    @Test
    void fail_closed_rejects_when_redis_is_unavailable() {
        properties.setFailOpen(false);
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> limiter.tryAcquireRpm("key", "chat", "req", 2))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getType())
                        .isEqualTo("rate_limit_error"));
    }

    @Test
    void tpm_without_limit_passes_without_redis() {
        assertThat(limiter.tryAcquireTpm("key", "chat", "req", 100, null)).isTrue();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void tpm_denies_when_script_returns_zero() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThat(limiter.tryAcquireTpm("key", "chat", "req", 100, 1000)).isFalse();
    }

    @Test
    void settle_tpm_swallows_redis_error() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.settleTpm("key", "chat", "req", 100, 30);
    }

    @Test
    @SuppressWarnings("unchecked")
    void daily_budget_uses_date_key() {
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.tryReserveBudget("key", "daily", new BigDecimal("1"), new BigDecimal("0.1"));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertThat(keys.getValue().get(0)).contains("20261102");
    }

    @Test
    @SuppressWarnings("unchecked")
    void monthly_budget_uses_month_key() {
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.tryReserveBudget("key", "monthly", new BigDecimal("1"), new BigDecimal("0.1"));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertThat(keys.getValue().get(0)).contains("202611");
    }

    @Test
    void budget_without_config_passes_without_redis() {
        assertThat(limiter.tryReserveBudget("key", null, new BigDecimal("1"), BigDecimal.TEN)).isTrue();
        assertThat(limiter.tryReserveBudget("key", "daily", null, BigDecimal.TEN)).isTrue();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void budget_denied_when_script_returns_zero() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThat(limiter.tryReserveBudget("key", "daily",
                new BigDecimal("0.01"), new BigDecimal("0.02"))).isFalse();
    }

    @Test
    void release_budget_swallows_redis_error() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.releaseBudget("key", "daily", BigDecimal.ONE);
    }
}
