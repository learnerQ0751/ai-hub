package com.qtua.aihub.common.rag;

import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.EmbeddingTokenEstimator;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 文本向量化端口。
 *
 * <p>业务层只依赖本接口；实现包括本地 LangChain4j、确定性测试实现与 OpenAI 兼容远端模型。
 * 模型标识与维度随索引元数据落库，用于模型版本隔离；最大输入窗口与单价供 chunk 校验与
 * 成本计算使用。</p>
 */
public interface EmbeddingModel {

    /**
     * 模型标识（含版本），写入文档/切片/向量元数据。
     *
     * @return 模型标识
     */
    String modelId();

    /**
     * 向量维度。
     *
     * @return 维度
     */
    int dimension();

    /**
     * 单条文本最大输入 token 数。
     *
     * @return 最大输入 token
     */
    int maxInputTokens();

    /**
     * 按输入 token 数计算成本。
     *
     * @param inputTokens 输入 token 数
     * @return 成本（8 位小数）；本地/确定性实现为 0
     */
    BigDecimal costOfInputTokens(long inputTokens);

    /**
     * 单条文本向量化。
     *
     * @param text 文本
     * @return 向量（长度必须等于 {@link #dimension()}）
     */
    float[] embed(String text);

    /**
     * 批量文本向量化；返回顺序与入参一致。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> embedAll(List<String> texts);

    /**
     * 单条文本向量化并返回输入 token 用量。
     *
     * @param text       文本
     * @param dimensions 请求维度；null 表示使用模型默认维度
     * @return 向量与 token 用量
     */
    default EmbeddingResult embedWithUsage(String text, Integer dimensions) {
        validateDimensions(dimensions);
        float[] vector = embed(text);
        return new EmbeddingResult(List.of(vector), estimateTokens(List.of(text)));
    }

    /**
     * 批量向量化并返回输入 token 用量。
     *
     * @param texts      文本列表
     * @param dimensions 请求维度；null 表示使用模型默认维度
     * @return 向量与 token 用量
     */
    default EmbeddingResult embedAllWithUsage(List<String> texts, Integer dimensions) {
        validateDimensions(dimensions);
        List<float[]> vectors = embedAll(texts);
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("embedding result size mismatch");
        }
        return new EmbeddingResult(vectors, estimateTokens(texts));
    }

    /**
     * 估算输入 token 数；默认使用统一估算规则，远端实现可覆盖为供应商 usage。
     *
     * @param texts 文本列表
     * @return token 估算
     */
    default long estimateTokens(List<String> texts) {
        return EmbeddingTokenEstimator.estimateAll(texts);
    }

    /**
     * 校验请求维度；本地/确定性实现只支持模型固定维度。
     *
     * @param dimensions 请求维度；null 表示不指定
     */
    default void validateDimensions(Integer dimensions) {
        if (ObjectUtils.isEmpty(dimensions)) {
            return;
        }
        if (dimensions <= 0 || dimensions != dimension()) {
            throw ChatCompletionException.invalidRequest(
                    "当前模型仅支持维度 " + dimension() + "，不支持 dimensions=" + dimensions,
                    "dimensions");
        }
    }
}
