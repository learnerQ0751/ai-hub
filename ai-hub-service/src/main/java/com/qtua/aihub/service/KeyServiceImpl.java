package com.qtua.aihub.service;

import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.KeyGenerateReqDTO;
import com.qtua.aihub.api.model.res.KeyBlockResDTO;
import com.qtua.aihub.api.model.res.KeyGenerateResDTO;
import com.qtua.aihub.api.model.res.KeyInfoResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.util.KeyHasher;
import com.qtua.aihub.manager.ApiKeyManager;
import com.qtua.aihub.manager.model.ApiKeyBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 虚拟 Key 应用服务。
 *
 * <p>负责明文生成、SHA-256 哈希、有效期换算、鉴权与封禁编排；不保存请求级状态。
 * 明文 Key 只出现在生成响应中，日志与数据库均不记录。</p>
 */
@Service
public class KeyServiceImpl implements KeyService {

    private static final Logger LOG = LoggerFactory.getLogger(KeyServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "sk-";
    private static final String MASK = "****";
    private static final int KEY_RANDOM_BYTES = 24;
    private static final int MASK_VISIBLE_LENGTH = 4;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_DAY = 86400L;
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$");
    private static final String BUDGET_DURATION_DAILY = "daily";
    private static final String BUDGET_DURATION_MONTHLY = "monthly";

    private final ApiKeyManager apiKeyManager;

    /**
     * 构造服务。
     *
     * @param apiKeyManager Key 数据门面
     */
    public KeyServiceImpl(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;
    }

    /**
     * 生成虚拟 Key 并落库（只存哈希）。
     *
     * @param reqDTO 生成参数
     * @return 一次性明文 Key 与元信息
     */
    @Override
    public KeyGenerateResDTO generate(KeyGenerateReqDTO reqDTO) {
        String budgetDuration = normalizeBudgetDuration(reqDTO.budgetDuration());
        String plainKey = KEY_PREFIX + randomHex();
        String keyHash = KeyHasher.sha256Hex(plainKey);
        Instant createdAt = Instant.now();
        Instant expiresAt = resolveExpiry(reqDTO.duration(), createdAt);
        ApiKeyBO apiKey = new ApiKeyBO(keyHash, reqDTO.models(), BigDecimal.ZERO,
                reqDTO.maxBudget(), budgetDuration, reqDTO.rpmLimit(), reqDTO.tpmLimit(),
                expiresAt, false, reqDTO.metadata(), createdAt);
        apiKeyManager.save(apiKey);
        // 只记录哈希用于审计关联，绝不记录明文 Key。
        LOG.info("generated virtual key hash={} models={} rpm={} tpm={} budget={} expiresAt={}",
                keyHash, reqDTO.models(), reqDTO.rpmLimit(), reqDTO.tpmLimit(),
                reqDTO.maxBudget(), expiresAt);
        return new KeyGenerateResDTO(plainKey, reqDTO.models(), BigDecimal.ZERO, reqDTO.metadata(),
                toIso(expiresAt), reqDTO.maxBudget(), budgetDuration,
                reqDTO.rpmLimit(), reqDTO.tpmLimit(), false);
    }

    /**
     * 校验并归一化预算周期。
     *
     * @param budgetDuration 预算周期；空表示不启用
     * @return daily/monthly 或 null
     */
    private String normalizeBudgetDuration(String budgetDuration) {
        if (!StringUtils.hasText(budgetDuration)) {
            return null;
        }
        String normalized = budgetDuration.trim().toLowerCase();
        if (!BUDGET_DURATION_DAILY.equals(normalized)
                && !BUDGET_DURATION_MONTHLY.equals(normalized)) {
            throw ChatCompletionException.invalidRequest(
                    "字段 'budget_duration' 必须为 daily 或 monthly", "budget_duration");
        }
        return normalized;
    }

    /**
     * 校验虚拟 Key：存在、未封禁、未过期。
     *
     * @param plainKey 明文 Key
     * @return 认证上下文
     */
    @Override
    public KeyAuthDTO authenticate(String plainKey) {
        if (!StringUtils.hasText(plainKey)) {
            throw ChatCompletionException.invalidApiKey();
        }
        ApiKeyBO apiKey = apiKeyManager.findByKeyHash(KeyHasher.sha256Hex(plainKey))
                .orElseThrow(ChatCompletionException::invalidApiKey);
        if (apiKey.isBlocked()) {
            throw ChatCompletionException.blockedApiKey();
        }
        if (!Objects.isNull(apiKey.getExpiresAt()) && !Instant.now().isBefore(apiKey.getExpiresAt())) {
            throw ChatCompletionException.expiredApiKey();
        }
        return KeyAuthDTO.virtualKey(apiKey.getKeyHash(), apiKey.getModels(),
                apiKey.getRpmLimit(), apiKey.getTpmLimit(),
                apiKey.getMaxBudget(), apiKey.getBudgetDuration());
    }

    /**
     * 查询 Key 信息。
     *
     * @param plainKey 明文 Key
     * @return 元信息（Key 掩码）
     */
    @Override
    public KeyInfoResDTO info(String plainKey) {
        ApiKeyBO apiKey = requireKey(plainKey);
        KeyInfoResDTO.KeyInfo info = new KeyInfoResDTO.KeyInfo(
                apiKey.getSpend(), apiKey.getSpend(), apiKey.getModels(),
                toIso(apiKey.getExpiresAt()), apiKey.getMaxBudget(), apiKey.getBudgetDuration(),
                apiKey.getRpmLimit(), apiKey.getTpmLimit(), apiKey.isBlocked(),
                toIso(apiKey.getCreatedAt()), apiKey.getMetadata(), null);
        return new KeyInfoResDTO(mask(plainKey), info);
    }

    /**
     * 封禁 Key。
     *
     * @param plainKey 明文 Key
     * @return 封禁结果
     */
    @Override
    public KeyBlockResDTO block(String plainKey) {
        return updateBlocked(plainKey, true);
    }

    /**
     * 解封 Key。
     *
     * @param plainKey 明文 Key
     * @return 解封结果
     */
    @Override
    public KeyBlockResDTO unblock(String plainKey) {
        return updateBlocked(plainKey, false);
    }

    private KeyBlockResDTO updateBlocked(String plainKey, boolean blocked) {
        ApiKeyBO apiKey = requireKey(plainKey);
        apiKeyManager.updateBlocked(apiKey.getKeyHash(), blocked);
        LOG.info("virtual key hash={} blocked={}", apiKey.getKeyHash(), blocked);
        return new KeyBlockResDTO(mask(plainKey), blocked);
    }

    private ApiKeyBO requireKey(String plainKey) {
        if (!StringUtils.hasText(plainKey)) {
            throw ChatCompletionException.invalidRequest("字段 'key' 不能为空", "key");
        }
        return apiKeyManager.findByKeyHash(KeyHasher.sha256Hex(plainKey))
                .orElseThrow(ChatCompletionException::apiKeyNotFound);
    }

    private String randomHex() {
        byte[] buffer = new byte[KEY_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    /**
     * 把 duration（如 1h/7d）换算为绝对过期时间。
     *
     * <p>使用 UTC {@link Instant}，避免服务器时区影响。</p>
     *
     * @param duration 有效期文本；空表示不过期
     * @param now      当前时间
     * @return 过期时间；不过期时为 null
     */
    private Instant resolveExpiry(String duration, Instant now) {
        if (!StringUtils.hasText(duration)) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(duration.trim());
        if (!matcher.matches()) {
            throw ChatCompletionException.invalidRequest(
                    "字段 'duration' 必须匹配 <数字><s|m|h|d> 格式", "duration");
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            throw ChatCompletionException.invalidRequest(
                    "字段 'duration' 必须为正数", "duration");
        }
        return now.plusSeconds(toSeconds(amount, matcher.group(2).charAt(0)));
    }

    private long toSeconds(long amount, char unit) {
        return switch (unit) {
            case 's' -> amount;
            case 'm' -> amount * SECONDS_PER_MINUTE;
            case 'h' -> amount * SECONDS_PER_HOUR;
            case 'd' -> amount * SECONDS_PER_DAY;
            default -> throw ChatCompletionException.invalidRequest(
                    "字段 'duration' 必须匹配 <数字><s|m|h|d> 格式", "duration");
        };
    }

    private String mask(String plainKey) {
        if (!StringUtils.hasText(plainKey) || plainKey.length() <= MASK_VISIBLE_LENGTH) {
            return KEY_PREFIX + MASK;
        }
        return KEY_PREFIX + MASK + plainKey.substring(plainKey.length() - MASK_VISIBLE_LENGTH);
    }

    private String toIso(Instant instant) {
        return ObjectUtils.isEmpty(instant) ? null : instant.toString();
    }
}
