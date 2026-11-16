package com.qtua.aihub.integration.ratelimit;

import com.qtua.aihub.api.KeyService;

import com.qtua.aihub.TestKeys;
import com.qtua.aihub.gateway.GatewayApplication;
import com.qtua.aihub.common.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-1.4 限流与预算集成测试。
 *
 * <p>使用 Testcontainers 启动真实 Redis；无 Docker 时自动跳过。
 * 并发用例验证多实例共享同一 Redis 时计数一致。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = GatewayApplication.class)
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private KeyService keyService;

    private String dataKey;

    /**
     * 数据面测试统一使用虚拟 Key（master key 仅控制面可用）。
     */
    @org.junit.jupiter.api.BeforeEach
    void initDataPlaneKey() {
        dataKey = TestKeys.create(keyService, "test-tenant", "test-project");
    }

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void rpm_allows_limit_then_rejects() {
        String key = "rpm-key";

        assertThat(rateLimiter.tryAcquireRpm(key, "chat", "r1", 2)).isTrue();
        assertThat(rateLimiter.tryAcquireRpm(key, "chat", "r2", 2)).isTrue();
        assertThat(rateLimiter.tryAcquireRpm(key, "chat", "r3", 2)).isFalse();
    }

    @Test
    void tpm_settlement_replaces_reservation() {
        String key = "tpm-key";

        assertThat(rateLimiter.tryAcquireTpm(key, "chat", "r1", 60, 100)).isTrue();
        assertThat(rateLimiter.tryAcquireTpm(key, "chat", "r2", 60, 100)).isFalse();

        rateLimiter.settleTpm(key, "chat", "r1", 60, 10);

        assertThat(rateLimiter.tryAcquireTpm(key, "chat", "r3", 60, 100)).isTrue();
    }

    @Test
    void budget_reserve_settle_and_exhaust() {
        String key = "budget-key";

        assertThat(rateLimiter.tryReserveBudget(key, "daily",
                new BigDecimal("0.0001"), new BigDecimal("0.00006"))).isTrue();
        assertThat(rateLimiter.tryReserveBudget(key, "daily",
                new BigDecimal("0.0001"), new BigDecimal("0.00006"))).isFalse();

        rateLimiter.settleBudget(key, "daily",
                new BigDecimal("0.00006"), new BigDecimal("0.00002"));

        assertThat(rateLimiter.tryReserveBudget(key, "daily",
                new BigDecimal("0.0001"), new BigDecimal("0.00006"))).isTrue();
    }

    @Test
    void rpm_window_recovers_after_window() {
        String key = "window-key";
        RateLimitProperties properties = new RateLimitProperties();
        RedisRateLimiter t0 = new RedisRateLimiter(redisTemplate, properties,
                Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC));
        RedisRateLimiter t61 = new RedisRateLimiter(redisTemplate, properties,
                Clock.fixed(Instant.parse("2026-11-02T10:01:01Z"), ZoneOffset.UTC));

        assertThat(t0.tryAcquireRpm(key, "chat", "r1", 2)).isTrue();
        assertThat(t0.tryAcquireRpm(key, "chat", "r2", 2)).isTrue();
        assertThat(t0.tryAcquireRpm(key, "chat", "r3", 2)).isFalse();
        assertThat(t61.tryAcquireRpm(key, "chat", "r4", 2)).isTrue();
    }

    @Test
    void budget_period_switch_resets_counter() {
        String key = "period-key";
        RateLimitProperties properties = new RateLimitProperties();
        RedisRateLimiter day1 = new RedisRateLimiter(redisTemplate, properties,
                Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC));
        RedisRateLimiter day2 = new RedisRateLimiter(redisTemplate, properties,
                Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC));

        assertThat(day1.tryReserveBudget(key, "daily",
                new BigDecimal("0.0001"), new BigDecimal("0.00008"))).isTrue();
        assertThat(day1.tryReserveBudget(key, "daily",
                new BigDecimal("0.0001"), new BigDecimal("0.00008"))).isFalse();
        assertThat(day2.tryReserveBudget(key, "daily",
                new BigDecimal("0.0001"), new BigDecimal("0.00008"))).isTrue();
    }

    @Test
    void concurrent_requests_share_the_same_counter() throws Exception {
        String key = "concurrent-key";
        int threads = 32;
        int limit = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return rateLimiter.tryAcquireRpm(key, "chat",
                            UUID.randomUUID().toString(), limit);
                }));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(limit);
        } finally {
            pool.shutdownNow();
        }
    }
}
