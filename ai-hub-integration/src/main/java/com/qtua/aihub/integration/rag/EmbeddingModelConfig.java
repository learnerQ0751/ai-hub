package com.qtua.aihub.integration.rag;

import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingModelProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RAG 主 embedding 模型装配。
 *
 * <p>RAG 业务（索引/检索/chunk 校验）只注入一个 {@link EmbeddingModel}，由模型目录按
 * {@code gateway.rag.embedding.model} 选择；`/v1/embeddings` 通过
 * {@link EmbeddingModelProvider} 按别名解析。</p>
 */
@Configuration
public class EmbeddingModelConfig {

    /**
     * RAG 主模型。
     *
     * @param provider 模型目录
     * @return 主模型
     */
    @Bean
    @Primary
    public EmbeddingModel ragEmbeddingModel(EmbeddingModelProvider provider) {
        return provider.primaryModel();
    }
}
