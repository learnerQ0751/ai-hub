package com.qtua.aihub.integration.rag;

import com.qtua.aihub.common.rag.EmbeddingModel;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 确定性 embedding（测试 / 离线占位）。
 *
 * <p>用 SHA-256 字节扩展为指定维度的单位向量：同一文本永远得到同一向量，
 * 不同文本近似随机。仅用于测试与无模型环境，不用于生产语义检索；成本恒为 0。</p>
 */
public class DeterministicEmbeddingModel implements EmbeddingModel {

    private static final String ALGORITHM = "SHA-256";
    private static final int BYTE_MASK = 0xFF;
    private static final double BYTE_RANGE = 127.5d;
    private static final double ONE = 1.0d;
    private static final double NORM_EPSILON = 0.0d;
    private static final float FIRST_COMPONENT = 1.0f;

    private final String modelId;
    private final int dimension;
    private final int maxInputTokens;

    /**
     * 构造确定性模型。
     *
     * @param definition 模型配置
     */
    public DeterministicEmbeddingModel(EmbeddingModelDefinition definition) {
        this.modelId = definition.model();
        this.dimension = definition.dimension();
        this.maxInputTokens = definition.maxInputTokens();
        if (dimension <= 0) {
            throw new IllegalStateException("RAG embedding dimension must be positive");
        }
    }

    /**
     * 模型标识。
     *
     * @return 模型标识
     */
    @Override
    public String modelId() {
        return modelId;
    }

    /**
     * 向量维度。
     *
     * @return 维度
     */
    @Override
    public int dimension() {
        return dimension;
    }

    /**
     * 单条最大输入 token。
     *
     * @return 最大输入 token
     */
    @Override
    public int maxInputTokens() {
        return maxInputTokens;
    }

    /**
     * 确定性实现无费用。
     *
     * @param inputTokens 输入 token 数
     * @return 0
     */
    @Override
    public BigDecimal costOfInputTokens(long inputTokens) {
        return BigDecimal.ZERO;
    }

    /**
     * 单条文本向量化。
     *
     * @param text 文本
     * @return 单位向量
     */
    @Override
    public float[] embed(String text) {
        byte[] digest = digest(text);
        float[] vector = new float[dimension];
        double norm = NORM_EPSILON;
        for (int index = 0; index < dimension; index++) {
            int value = digest[index % digest.length] & BYTE_MASK;
            vector[index] = (float) (value / BYTE_RANGE - ONE);
            norm += vector[index] * vector[index];
        }
        if (norm <= NORM_EPSILON) {
            vector[0] = FIRST_COMPONENT;
            return vector;
        }
        double sqrt = Math.sqrt(norm);
        for (int index = 0; index < dimension; index++) {
            vector[index] = (float) (vector[index] / sqrt);
        }
        return vector;
    }

    /**
     * 批量文本向量化。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    @Override
    public List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    private byte[] digest(String text) {
        try {
            return MessageDigest.getInstance(ALGORITHM)
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
