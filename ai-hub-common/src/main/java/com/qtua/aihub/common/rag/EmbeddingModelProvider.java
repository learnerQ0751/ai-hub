package com.qtua.aihub.common.rag;

import java.util.Optional;
import java.util.Set;

/**
 * embedding 模型目录端口。
 *
 * <p>按别名解析模型（{@code /v1/embeddings} 的 model 字段）或按模型标识解析
 * （RAG 元数据/治理计价）；同时提供 RAG 使用的主模型。实现由配置构建，只读、无状态。</p>
 */
public interface EmbeddingModelProvider {

    /**
     * 按别名查询模型。
     *
     * @param alias 模型别名
     * @return 模型；未知别名返回空
     */
    Optional<EmbeddingModel> findByAlias(String alias);

    /**
     * 按模型标识查询模型。
     *
     * @param modelId 模型标识（写入索引元数据的值）
     * @return 模型；未匹配返回空
     */
    Optional<EmbeddingModel> findByModelId(String modelId);

    /**
     * RAG 使用的主模型。
     *
     * @return 主模型
     */
    EmbeddingModel primaryModel();

    /**
     * 全部模型别名。
     *
     * @return 不可变别名集合
     */
    Set<String> listAliases();
}
