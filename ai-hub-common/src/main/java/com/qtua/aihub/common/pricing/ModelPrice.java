package com.qtua.aihub.common.pricing;

import com.qtua.aihub.common.metering.TokenUsage;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 模型价格。
 *
 * <p>单价口径为「每 1M tokens」，金额使用 {@link BigDecimal} 计算，
 * 保留 8 位小数并按 HALF_UP 舍入，与 {@code api_keys.spend} 精度一致。</p>
 *
 * @param inputPerMillion  输入单价（每 1M tokens）
 * @param outputPerMillion 输出单价（每 1M tokens）
 */
public record ModelPrice(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {

    /** 计价单位：1M tokens。 */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    /** 金额精度：与数据库 NUMERIC(18,8) 一致。 */
    private static final int SCALE = 8;

    /**
     * 校验单价非空且非负。
     */
    public ModelPrice {
        if (ObjectUtils.isEmpty(inputPerMillion) || ObjectUtils.isEmpty(outputPerMillion)) {
            throw new IllegalArgumentException("Model price must not be null");
        }
        if (inputPerMillion.signum() < 0 || outputPerMillion.signum() < 0) {
            throw new IllegalArgumentException("Model price must not be negative");
        }
    }

    /**
     * 按用量计算成本。
     *
     * @param usage Token 用量
     * @return 成本（8 位小数）
     */
    public BigDecimal costOf(TokenUsage usage) {
        return costOf(usage.promptTokens(), usage.completionTokens());
    }

    /**
     * 按输入/输出 Token 数计算成本。
     *
     * <p>先乘后除，避免过早舍入造成精度损失。</p>
     *
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     * @return 成本（8 位小数）
     */
    public BigDecimal costOf(long promptTokens, long completionTokens) {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("Token usage must not be negative");
        }
        BigDecimal inputCost = inputPerMillion.multiply(BigDecimal.valueOf(promptTokens))
                .divide(MILLION, SCALE, RoundingMode.HALF_UP);
        BigDecimal outputCost = outputPerMillion.multiply(BigDecimal.valueOf(completionTokens))
                .divide(MILLION, SCALE, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
