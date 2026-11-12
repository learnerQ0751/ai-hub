package com.qtua.aihub.integration.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.config.RagProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-3.7 embedding 模型目录：旧单模型兼容、多模型切换、远端配置校验。
 */
class ConfigurableEmbeddingModelProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacy_single_model_config_still_works() {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setModel("det-alias");
        properties.getEmbedding().setProvider("deterministic");
        properties.getEmbedding().setModelId("det-v1");
        properties.getEmbedding().setDimension(2);
        properties.getEmbedding().setMaxInputTokens(100);

        ConfigurableEmbeddingModelProvider provider =
                new ConfigurableEmbeddingModelProvider(properties, objectMapper);

        assertThat(provider.listAliases()).containsExactly("det-alias");
        assertThat(provider.primaryModel().modelId()).isEqualTo("det-v1");
        assertThat(provider.findByAlias("det-alias")).isPresent();
        assertThat(provider.findByModelId("det-v1")).isPresent();
        provider.shutdown();
    }

    @Test
    void selects_primary_alias_from_models_catalog() {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setModel("emb-b");
        java.util.Map<String, RagProperties.ModelSettings> models = new java.util.LinkedHashMap<>();
        models.put("emb-a", deterministic(3, "emb-a-v1"));
        models.put("emb-b", deterministic(4, "emb-b-v1"));
        properties.getEmbedding().setModels(models);

        ConfigurableEmbeddingModelProvider provider =
                new ConfigurableEmbeddingModelProvider(properties, objectMapper);

        assertThat(provider.listAliases()).containsExactly("emb-a", "emb-b");
        assertThat(provider.primaryModel().modelId()).isEqualTo("emb-b-v1");
        assertThat(provider.findByAlias("emb-a").orElseThrow().dimension()).isEqualTo(3);
        provider.shutdown();
    }

    @Test
    void rejects_remote_model_without_api_base() {
        RagProperties properties = new RagProperties();
        RagProperties.ModelSettings settings = new RagProperties.ModelSettings();
        settings.setProvider("dashscope");
        settings.setModel("text-embedding-v3");
        settings.setDimension(1024);
        settings.setMaxInputTokens(8192);
        settings.setMaxBatchSize(10);
        properties.getEmbedding().setModels(java.util.Map.of("v3", settings));

        assertThatThrownBy(() -> new ConfigurableEmbeddingModelProvider(properties, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-base");
    }

    private RagProperties.ModelSettings deterministic(int dimension, String model) {
        RagProperties.ModelSettings settings = new RagProperties.ModelSettings();
        settings.setProvider("deterministic");
        settings.setModel(model);
        settings.setDimension(dimension);
        settings.setMaxInputTokens(100);
        settings.setMaxBatchSize(4);
        return settings;
    }
}
