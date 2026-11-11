package com.qtua.aihub.common.rag;

import java.util.List;

/**
 * 文本向量化端口。
 *
 * <p>业务层只依赖本接口；默认实现是 LangChain4j 本地 all-minilm-l6-v2（384 维），
 * 测试/离线可切换确定性实现。模型标识与维度随索引元数据落库，用于模型版本隔离。</p>
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
}
