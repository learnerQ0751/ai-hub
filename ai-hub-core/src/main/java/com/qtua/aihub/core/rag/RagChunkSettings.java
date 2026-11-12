package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.metering.EmbeddingTokenEstimator;
import com.qtua.aihub.common.rag.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 切片参数解析与模型窗口校验。
 *
 * <p>chunk 大小默认按 embedding 模型 {@code max-input-tokens × 0.8} 推导（中文按 1 字≈1 token），
 * 并收敛到 800 字上限；显式配置优先。超出安全窗口时按 {@code overflow-policy}
 * 记录 WARNING 或启动失败，避免切片被供应商静默截断。</p>
 */
@Component
public class RagChunkSettings {

    private static final Logger LOG = LoggerFactory.getLogger(RagChunkSettings.class);

    /** 中文切片推荐上限。 */
    public static final int MAX_CHARS_CAP = 800;
    /** 模型窗口安全系数。 */
    public static final double SAFETY_FACTOR = 0.8d;
    private static final int MIN_MAX_CHARS = 2;
    private static final int MIN_OVERLAP = 0;
    private static final String POLICY_FAIL = "fail";

    private final int maxChars;
    private final int overlapChars;

    /**
     * 构造并校验切片参数。
     *
     * @param properties     RAG 配置
     * @param embeddingModel 当前使用的 embedding 模型
     */
    public RagChunkSettings(RagProperties properties, EmbeddingModel embeddingModel) {
        int window = Math.max(1, embeddingModel.maxInputTokens());
        int safeTokens = Math.max(1, (int) Math.floor(window * SAFETY_FACTOR));
        int configured = properties.getChunk().getMaxChars();
        int derived = Math.max(MIN_MAX_CHARS, Math.min(MAX_CHARS_CAP, safeTokens));
        this.maxChars = configured > 0 ? Math.max(MIN_MAX_CHARS, configured) : derived;
        this.overlapChars = Math.max(MIN_OVERLAP,
                Math.min(properties.getChunk().getOverlapChars(), maxChars - 1));
        validate(properties.getChunk().getOverflowPolicy(), safeTokens, configured);
    }

    /**
     * 有效切片最大字符数。
     *
     * @return 最大字符数
     */
    public int maxChars() {
        return maxChars;
    }

    /**
     * 有效重叠字符数。
     *
     * @return 重叠字符数
     */
    public int overlapChars() {
        return overlapChars;
    }

    private void validate(String policy, int safeTokens, int configured) {
        long estimatedTokens = EmbeddingTokenEstimator.estimateChineseChars(maxChars);
        if (estimatedTokens <= safeTokens) {
            return;
        }
        String message = "chunk maxChars=" + maxChars + " (" + estimatedTokens
                + " tokens) exceeds the embedding safety window " + safeTokens
                + " tokens; chunks may be truncated. recommended maxChars<="
                + Math.min(MAX_CHARS_CAP, safeTokens);
        if (POLICY_FAIL.equalsIgnoreCase(policy)) {
            throw new IllegalStateException(message);
        }
        LOG.warn("{}{}", message, configured > 0 ? " (explicit chunk configuration)" : "");
    }
}
