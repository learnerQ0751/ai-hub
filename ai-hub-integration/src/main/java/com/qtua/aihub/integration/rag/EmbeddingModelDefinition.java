package com.qtua.aihub.integration.rag;

import java.math.BigDecimal;

/**
 * 单个 embedding 模型的有效配置（已合并模型条目与旧单模型默认值）。
 *
 * <p>只读值对象；由 {@link ConfigurableEmbeddingModelProvider} 构建后传给具体实现。</p>
 *
 * @param alias            模型别名
 * @param provider         提供方
 * @param model            上游真实模型名
 * @param dimension        向量维度
 * @param maxInputTokens   单条最大输入 token
 * @param maxBatchSize     单次请求最大条数
 * @param inputPerMillion  输入单价（每 1M tokens）
 * @param apiBase          OpenAI 兼容 base-url
 * @param apiKey           API Key（可能为空）
 * @param apiKeyEnv        API Key 环境变量名（诊断用）
 * @param timeoutMs        连接/读超时
 * @param maxRetries       重试次数
 * @param retryBackoffMs   重试退避基数
 * @param threads          本地模型线程数
 */
public record EmbeddingModelDefinition(
        String alias,
        String provider,
        String model,
        int dimension,
        int maxInputTokens,
        int maxBatchSize,
        BigDecimal inputPerMillion,
        String apiBase,
        String apiKey,
        String apiKeyEnv,
        int timeoutMs,
        int maxRetries,
        long retryBackoffMs,
        int threads) {

    /**
     * 脱敏输出：不包含 apiKey。
     *
     * @return 可安全打印的描述
     */
    @Override
    public String toString() {
        return "EmbeddingModelDefinition[alias=" + alias
                + ", provider=" + provider
                + ", model=" + model
                + ", dimension=" + dimension
                + ", maxInputTokens=" + maxInputTokens
                + ", maxBatchSize=" + maxBatchSize
                + ", apiBase=" + apiBase + "]";
    }
}
