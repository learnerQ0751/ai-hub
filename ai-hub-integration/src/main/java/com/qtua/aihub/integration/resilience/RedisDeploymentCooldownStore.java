package com.qtua.aihub.integration.resilience;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.common.resilience.DeploymentHealthStore;
import com.qtua.aihub.common.util.KeyHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.Duration;

/**
 * 基于 Redis 的部署冷却状态存储。
 *
 * <p>失败计数与冷却标记全部外置 Redis，多实例共享：
 * <ul>
 *   <li>{@code {resilience}:fails:<deployment>}：连续失败计数，带 TTL 自动过期；</li>
 *   <li>{@code {resilience}:cooldown:<deployment>}：冷却标记，TTL 到期自动恢复。</li>
 * </ul>
 * 部署标识由别名 + 上游模型 + api-base 计算 SHA-256，不包含 api-key。
 * Redis 故障时 fail-open：冷却判断返回 false，记录操作忽略并告警。</p>
 */
@Component
public class RedisDeploymentCooldownStore implements DeploymentHealthStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisDeploymentCooldownStore.class);

    private static final String KEY_PREFIX = "{resilience}:";
    private static final String FAILS_SEGMENT = "fails:";
    private static final String COOLDOWN_SEGMENT = "cooldown:";
    private static final String COOLDOWN_VALUE = "1";
    private static final int MIN_SECONDS = 1;
    private static final int MIN_FAILURE_TTL_SECONDS = 60;
    private static final int TTL_MULTIPLIER = 2;

    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties.Resilience resilience;

    /**
     * 构造冷却存储。
     *
     * @param redisTemplate Redis 客户端
     * @param properties    网关配置（读取 resilience 节）
     */
    public RedisDeploymentCooldownStore(StringRedisTemplate redisTemplate,
                                        GatewayProperties properties) {
        this.redisTemplate = redisTemplate;
        this.resilience = properties.getResilience();
    }

    /**
     * 判断部署是否冷却中；Redis 故障 fail-open。
     *
     * @param deployment 部署
     * @return true 表示冷却中
     */
    @Override
    public boolean isCoolingDown(ModelDeployment deployment) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(deployment)));
        } catch (RuntimeException ex) {
            LOG.warn("resilience store unavailable, fail-open alias={} upstreamModel={}",
                    deployment.alias(), deployment.upstreamModel(), ex);
            return false;
        }
    }

    /**
     * 累加失败计数；达到阈值写入冷却标记。
     *
     * @param deployment 失败部署
     */
    @Override
    public void recordFailure(ModelDeployment deployment) {
        try {
            String failsKey = failsKey(deployment);
            Long failures = redisTemplate.opsForValue().increment(failsKey);
            if (ObjectUtils.isEmpty(failures)) {
                return;
            }
            redisTemplate.expire(failsKey, Duration.ofSeconds(failureTtlSeconds()));
            if (failures >= allowedFails()) {
                redisTemplate.opsForValue().set(cooldownKey(deployment), COOLDOWN_VALUE,
                        Duration.ofSeconds(cooldownSeconds()));
                LOG.warn("deployment entered cooldown alias={} upstreamModel={} failures={} cooldownSeconds={}",
                        deployment.alias(), deployment.upstreamModel(), failures, cooldownSeconds());
            }
        } catch (RuntimeException ex) {
            LOG.warn("failed to record deployment failure alias={} upstreamModel={}",
                    deployment.alias(), deployment.upstreamModel(), ex);
        }
    }

    /**
     * 成功后清除连续失败计数。
     *
     * @param deployment 成功部署
     */
    @Override
    public void recordSuccess(ModelDeployment deployment) {
        try {
            redisTemplate.delete(failsKey(deployment));
        } catch (RuntimeException ex) {
            LOG.warn("failed to clear deployment failures alias={} upstreamModel={}",
                    deployment.alias(), deployment.upstreamModel(), ex);
        }
    }

    private String failsKey(ModelDeployment deployment) {
        return KEY_PREFIX + FAILS_SEGMENT + deploymentKey(deployment);
    }

    private String cooldownKey(ModelDeployment deployment) {
        return KEY_PREFIX + COOLDOWN_SEGMENT + deploymentKey(deployment);
    }

    /**
     * 部署稳定标识：别名 + 上游模型 + api-base + api-key 的 SHA-256。
     *
     * <p>同一别名下可能存在上游模型与 base 相同、但 api-key 不同的主备部署
     * （例如灰度 key 或错误 key 的隔离），必须把 api-key 纳入哈希输入才能区分；
     * SHA-256 摘要不可逆，Redis 中不会出现明文密钥。</p>
     *
     * @param deployment 部署
     * @return 摘要
     */
    private String deploymentKey(ModelDeployment deployment) {
        return KeyHasher.sha256Hex(deployment.alias() + "|" + deployment.upstreamModel()
                + "|" + deployment.apiBase() + "|" + deployment.apiKey());
    }

    private int allowedFails() {
        return Math.max(1, resilience.getAllowedFails());
    }

    private int cooldownSeconds() {
        return Math.max(MIN_SECONDS, resilience.getCooldownSeconds());
    }

    private int failureTtlSeconds() {
        return Math.max(MIN_FAILURE_TTL_SECONDS, cooldownSeconds() * TTL_MULTIPLIER);
    }
}
