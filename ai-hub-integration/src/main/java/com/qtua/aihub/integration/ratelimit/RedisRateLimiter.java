package com.qtua.aihub.integration.ratelimit;

import com.qtua.aihub.common.config.RateLimitProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Redis 限流与预算计数器。
 *
 * <p>RPM/TPM 使用 ZSET 滑动窗口（窗口默认 60s），按 {@code key + model} 维度计数；
 * 预算按 key + 周期（daily/monthly）累计，金额以 micro 单位（1e-8 元）存 Redis 整数，
 * 避免浮点误差。所有 Lua 脚本原子执行，多实例共享同一 Redis，计数一致。</p>
 *
 * <p>Redis 故障时默认 fail-open（放行并告警），可通过配置改为 fail-closed。
 * 结算/释放失败只告警，不影响已完成的业务响应。</p>
 */
@Component
public class RedisRateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String KEY_PREFIX = "{rate_limit}:";
    private static final String RPM_PREFIX = KEY_PREFIX + "rpm:";
    private static final String TPM_PREFIX = KEY_PREFIX + "tpm:";
    private static final String BUDGET_PREFIX = KEY_PREFIX + "budget:";
    private static final String MEMBER_SEPARATOR = "|";
    private static final BigDecimal MICRO = BigDecimal.valueOf(100_000_000L);
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final int DAILY_TTL_SECONDS = 172_800;
    private static final int MONTHLY_TTL_SECONDS = 2_764_800;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /** RPM：ZSET 滑动窗口，member=requestId，按时间戳清理。 */
    private static final RedisScript<Long> RPM_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, member)
            redis.call('PEXPIRE', KEYS[1], window)
            return 1
            """, Long.class);

    /** TPM：member 形如 requestId|tokens，统计窗口内 token 总和。 */
    private static final RedisScript<Long> TPM_ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local addTokens = tonumber(ARGV[4])
            local member = ARGV[5]
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            local used = 0
            for i = 1, #members do
                local tokens = string.match(members[i], '|(%d+)$')
                if tokens then
                    used = used + tonumber(tokens)
                end
            end
            if used + addTokens > limit then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, member)
            redis.call('PEXPIRE', KEYS[1], window)
            return 1
            """, Long.class);

    /** TPM 结算：用实际 token 替换预占 member，保留原时间戳。 */
    private static final RedisScript<Long> TPM_SETTLE_SCRIPT = new DefaultRedisScript<>("""
            local oldMember = ARGV[1]
            local newMember = ARGV[2]
            local score = redis.call('ZSCORE', KEYS[1], oldMember)
            if score then
                redis.call('ZREM', KEYS[1], oldMember)
                redis.call('ZADD', KEYS[1], score, newMember)
            end
            return 1
            """, Long.class);

    /** 预算预占：spent + cost <= limit 才 INCRBY。 */
    private static final RedisScript<Long> BUDGET_RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local cost = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            local spent = tonumber(redis.call('GET', KEYS[1]) or '0')
            if limit >= 0 and spent + cost > limit then
                return 0
            end
            redis.call('INCRBY', KEYS[1], cost)
            redis.call('EXPIRE', KEYS[1], ttl)
            return 1
            """, Long.class);

    /** 预算结算：释放预占，计入实际成本。 */
    private static final RedisScript<Long> BUDGET_SETTLE_SCRIPT = new DefaultRedisScript<>("""
            local reserved = tonumber(ARGV[1])
            local actual = tonumber(ARGV[2])
            if reserved > 0 then
                redis.call('DECRBY', KEYS[1], reserved)
            end
            if actual > 0 then
                redis.call('INCRBY', KEYS[1], actual)
            end
            return 1
            """, Long.class);

    /** 预算释放：失败路径全额退回预占。 */
    private static final RedisScript<Long> BUDGET_RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local reserved = tonumber(ARGV[1])
            if reserved > 0 then
                redis.call('DECRBY', KEYS[1], reserved)
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final Clock clock;

    /**
     * 构造限流器。
     *
     * @param redisTemplate Redis 客户端
     * @param properties    限流配置
     */
    @Autowired
    public RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    /**
     * 测试用构造器，可注入固定时钟。
     *
     * @param redisTemplate Redis 客户端
     * @param properties    限流配置
     * @param clock         时钟
     */
    RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * RPM 滑动窗口检查。
     *
     * @param keyHash   虚拟 Key 哈希
     * @param model     模型别名
     * @param requestId 请求 ID
     * @param rpmLimit  每分钟请求上限；null 或 <=0 表示不限
     * @return 是否放行
     */
    public boolean tryAcquireRpm(String keyHash, String model, String requestId, Integer rpmLimit) {
        if (!properties.isEnabled() || ObjectUtils.isEmpty(rpmLimit) || rpmLimit <= 0) {
            return true;
        }
        String key = RPM_PREFIX + keyHash + ":" + model;
        long now = clock.millis();
        long window = properties.getWindowSeconds() * MILLIS_PER_SECOND;
        return execute(RPM_SCRIPT, key, String.valueOf(now), String.valueOf(window),
                String.valueOf(rpmLimit), requestId);
    }

    /**
     * TPM 滑动窗口预占。
     *
     * @param keyHash         虚拟 Key 哈希
     * @param model           模型别名
     * @param requestId       请求 ID
     * @param estimatedTokens 预占 Token 数
     * @param tpmLimit        每分钟 Token 上限；null 或 <=0 表示不限
     * @return 是否放行
     */
    public boolean tryAcquireTpm(String keyHash, String model, String requestId,
                                 long estimatedTokens, Integer tpmLimit) {
        if (!properties.isEnabled() || ObjectUtils.isEmpty(tpmLimit) || tpmLimit <= 0) {
            return true;
        }
        String key = TPM_PREFIX + keyHash + ":" + model;
        long now = clock.millis();
        long window = properties.getWindowSeconds() * MILLIS_PER_SECOND;
        String member = tpmMember(requestId, estimatedTokens);
        return execute(TPM_ACQUIRE_SCRIPT, key, String.valueOf(now), String.valueOf(window),
                String.valueOf(tpmLimit), String.valueOf(estimatedTokens), member);
    }

    /**
     * TPM 结算：用实际 Token 替换预占值。
     *
     * @param keyHash         虚拟 Key 哈希
     * @param model           模型别名
     * @param requestId       请求 ID
     * @param estimatedTokens 预占 Token 数
     * @param actualTokens    实际 Token 数
     */
    public void settleTpm(String keyHash, String model, String requestId,
                          long estimatedTokens, long actualTokens) {
        if (!properties.isEnabled() || estimatedTokens == actualTokens) {
            return;
        }
        String key = TPM_PREFIX + keyHash + ":" + model;
        try {
            redisTemplate.execute(TPM_SETTLE_SCRIPT, List.of(key),
                    tpmMember(requestId, estimatedTokens), tpmMember(requestId, actualTokens));
        } catch (RuntimeException ex) {
            LOG.warn("failed to settle tpm key={} requestId={}", key, requestId, ex);
        }
    }

    /**
     * 预算预占。
     *
     * @param keyHash        虚拟 Key 哈希
     * @param budgetDuration 预算周期：daily/monthly
     * @param maxBudget      预算上限；null 表示不限
     * @param estimatedCost  预占成本
     * @return 是否放行
     */
    public boolean tryReserveBudget(String keyHash, String budgetDuration,
                                    BigDecimal maxBudget, BigDecimal estimatedCost) {
        if (!properties.isEnabled() || ObjectUtils.isEmpty(maxBudget)
                || !StringUtils.hasText(budgetDuration)) {
            return true;
        }
        String key = budgetKey(keyHash, budgetDuration);
        long costMicro = toMicro(estimatedCost);
        long limitMicro = toMicro(maxBudget);
        int ttl = ttlSeconds(budgetDuration);
        return execute(BUDGET_RESERVE_SCRIPT, key, String.valueOf(costMicro),
                String.valueOf(limitMicro), String.valueOf(ttl));
    }

    /**
     * 预算结算：释放预占，计入实际成本。
     *
     * @param keyHash        虚拟 Key 哈希
     * @param budgetDuration 预算周期
     * @param reservedCost   预占成本
     * @param actualCost     实际成本
     */
    public void settleBudget(String keyHash, String budgetDuration,
                             BigDecimal reservedCost, BigDecimal actualCost) {
        if (!properties.isEnabled() || !StringUtils.hasText(budgetDuration)) {
            return;
        }
        String key = budgetKey(keyHash, budgetDuration);
        try {
            redisTemplate.execute(BUDGET_SETTLE_SCRIPT, List.of(key),
                    String.valueOf(toMicro(reservedCost)), String.valueOf(toMicro(actualCost)));
        } catch (RuntimeException ex) {
            LOG.warn("failed to settle budget key={}", key, ex);
        }
    }

    /**
     * 预算释放：失败路径退回预占。
     *
     * @param keyHash        虚拟 Key 哈希
     * @param budgetDuration 预算周期
     * @param reservedCost   预占成本
     */
    public void releaseBudget(String keyHash, String budgetDuration, BigDecimal reservedCost) {
        if (!properties.isEnabled() || !StringUtils.hasText(budgetDuration)) {
            return;
        }
        String key = budgetKey(keyHash, budgetDuration);
        try {
            redisTemplate.execute(BUDGET_RELEASE_SCRIPT, List.of(key),
                    String.valueOf(toMicro(reservedCost)));
        } catch (RuntimeException ex) {
            LOG.warn("failed to release budget key={}", key, ex);
        }
    }

    /**
     * 执行限流脚本；Redis 故障按 fail-open/closed 处理。
     *
     * @param script Lua 脚本
     * @param key    Redis key
     * @param args   参数
     * @return 是否放行
     */
    private boolean execute(RedisScript<Long> script, String key, String... args) {
        try {
            Long result = redisTemplate.execute(script, List.of(key), (Object[]) args);
            return !ObjectUtils.isEmpty(result) && result == 1L;
        } catch (RuntimeException ex) {
            if (properties.isFailOpen()) {
                LOG.warn("rate limiter unavailable, fail-open key={}", key, ex);
                return true;
            }
            LOG.error("rate limiter unavailable, fail-closed key={}", key, ex);
            throw ChatCompletionException.rateLimitExceeded("限流服务不可用");
        }
    }

    private String tpmMember(String requestId, long tokens) {
        return requestId + MEMBER_SEPARATOR + tokens;
    }

    private String budgetKey(String keyHash, String budgetDuration) {
        LocalDate today = LocalDate.now(clock);
        String period = "monthly".equals(budgetDuration)
                ? today.format(MONTH_FORMAT) : today.format(DateTimeFormatter.BASIC_ISO_DATE);
        return BUDGET_PREFIX + keyHash + ":" + period;
    }

    private int ttlSeconds(String budgetDuration) {
        return "monthly".equals(budgetDuration) ? MONTHLY_TTL_SECONDS : DAILY_TTL_SECONDS;
    }

    private long toMicro(BigDecimal amount) {
        return amount.multiply(MICRO).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
