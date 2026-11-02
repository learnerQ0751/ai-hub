package com.qtua.aihub.integration.resilience;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDeploymentCooldownStoreTest {

    private static final ModelDeployment DEPLOYMENT = new ModelDeployment(
            "chat", "https://example.com", "secret", "deepseek-chat", 1, 0);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private GatewayProperties.Resilience resilience;
    private RedisDeploymentCooldownStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        resilience = new GatewayProperties.Resilience();
        GatewayProperties properties = new GatewayProperties();
        properties.setResilience(resilience);
        store = new RedisDeploymentCooldownStore(redisTemplate, properties);
    }

    @Test
    void enters_cooldown_after_allowed_failures() {
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L);

        store.recordFailure(DEPLOYMENT);
        store.recordFailure(DEPLOYMENT);
        verify(valueOperations, never()).set(argThat(key -> key.contains("cooldown")),
                anyString(), any(Duration.class));

        store.recordFailure(DEPLOYMENT);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(argThat(key -> key.contains("cooldown")), eq("1"), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void reports_cooling_down_when_cooldown_key_exists() {
        when(redisTemplate.hasKey(argThat(key -> key.contains("cooldown")))).thenReturn(true);

        assertThat(store.isCoolingDown(DEPLOYMENT)).isTrue();
    }

    @Test
    void success_clears_failure_count() {
        store.recordSuccess(DEPLOYMENT);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(key.capture());
        assertThat(key.getValue()).contains("fails");
    }

    @Test
    void redis_failure_is_fail_open() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).increment(anyString());

        assertThat(store.isCoolingDown(DEPLOYMENT)).isFalse();
        store.recordFailure(DEPLOYMENT);
        store.recordSuccess(DEPLOYMENT);
    }

    @Test
    void deployment_key_does_not_contain_api_key() {
        store.recordFailure(DEPLOYMENT);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(key.capture());
        assertThat(key.getValue()).doesNotContain("secret");
        assertThat(key.getValue()).contains("resilience");
    }

    @Test
    void deployments_with_different_api_keys_use_different_keys() {
        ModelDeployment primary = new ModelDeployment(
                "chat", "https://example.com", "primary-key", "deepseek-chat", 1, 0);
        ModelDeployment backup = new ModelDeployment(
                "chat", "https://example.com", "backup-key", "deepseek-chat", 1, 1);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        store.recordFailure(primary);
        store.recordFailure(backup);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.times(2)).increment(key.capture());
        assertThat(key.getAllValues()).hasSize(2);
        assertThat(key.getAllValues().get(0)).isNotEqualTo(key.getAllValues().get(1));
    }
}
