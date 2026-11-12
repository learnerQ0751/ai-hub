package com.qtua.aihub.integration.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingModelProvider;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 按配置构建 embedding 模型目录。
 *
 * <p>支持两种配置：新目录 {@code gateway.rag.embedding.models.*}（多模型切换）与旧单模型字段
 * （测试/离线兼容）。目录只读、无状态；本地模型在容器销毁时统一关闭线程池。</p>
 */
@Component
public class ConfigurableEmbeddingModelProvider implements EmbeddingModelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurableEmbeddingModelProvider.class);

    private static final String PROVIDER_DASHSCOPE = "dashscope";
    private static final String PROVIDER_OPENAI = "openai-compatible";
    private static final String PROVIDER_LOCAL = "langchain4j-local";
    private static final String PROVIDER_DETERMINISTIC = "deterministic";
    private static final int DASHSCOPE_MAX_BATCH = 10;
    private static final int DEFAULT_REMOTE_MAX_INPUT_TOKENS = 8192;
    private static final int DEFAULT_LOCAL_MAX_INPUT_TOKENS = 256;
    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long DEFAULT_RETRY_BACKOFF_MS = 200L;
    private static final int DEFAULT_THREADS = 2;

    private final Map<String, EmbeddingModel> modelsByAlias;
    private final Map<String, EmbeddingModel> modelsById;
    private final Set<String> aliases;
    private final EmbeddingModel primaryModel;

    /**
     * 构造模型目录。
     *
     * @param properties   RAG 配置
     * @param objectMapper Jackson 2 序列化器（远端模型请求/响应）
     */
    public ConfigurableEmbeddingModelProvider(RagProperties properties, ObjectMapper objectMapper) {
        Map<String, EmbeddingModel> byAlias = new LinkedHashMap<>();
        Map<String, EmbeddingModel> byId = new LinkedHashMap<>();
        RagProperties.Embedding embedding = properties.getEmbedding();
        if (ObjectUtils.isEmpty(embedding.getModels())) {
            String alias = StringUtils.hasText(embedding.getModel()) ? embedding.getModel() : "default";
            EmbeddingModelDefinition definition = legacyDefinition(alias, embedding);
            register(byAlias, byId, definition, objectMapper);
        } else {
            embedding.getModels().forEach((alias, settings) ->
                    register(byAlias, byId, definitionOf(alias, settings, embedding), objectMapper));
        }
        if (byAlias.isEmpty()) {
            throw new IllegalStateException("未配置任何 embedding 模型（gateway.rag.embedding.models）");
        }
        this.modelsByAlias = Collections.unmodifiableMap(byAlias);
        this.modelsById = Collections.unmodifiableMap(byId);
        this.aliases = Collections.unmodifiableSet(new LinkedHashSet<>(byAlias.keySet()));
        String primaryAlias = StringUtils.hasText(embedding.getModel())
                ? embedding.getModel() : this.aliases.iterator().next();
        this.primaryModel = byAlias.getOrDefault(primaryAlias, byAlias.values().iterator().next());
        LOG.info("embedding model catalog ready: primary={}, aliases={}",
                primaryAlias, this.aliases);
    }

    /**
     * 按别名查询模型。
     *
     * @param alias 模型别名
     * @return 模型；未知别名返回空
     */
    @Override
    public Optional<EmbeddingModel> findByAlias(String alias) {
        if (!StringUtils.hasText(alias)) {
            return Optional.empty();
        }
        return Optional.ofNullable(modelsByAlias.get(alias.trim()));
    }

    /**
     * 按模型标识查询模型。
     *
     * @param modelId 模型标识
     * @return 模型；未匹配返回空
     */
    @Override
    public Optional<EmbeddingModel> findByModelId(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(modelsById.get(modelId.trim()));
    }

    /**
     * RAG 使用的主模型。
     *
     * @return 主模型
     */
    @Override
    public EmbeddingModel primaryModel() {
        return primaryModel;
    }

    /**
     * 全部模型别名。
     *
     * @return 不可变别名集合
     */
    @Override
    public Set<String> listAliases() {
        return aliases;
    }

    /**
     * 关闭本地模型线程池。
     */
    @PreDestroy
    public void shutdown() {
        modelsByAlias.values().stream()
                .filter(model -> model instanceof LangChain4jEmbeddingModel)
                .map(model -> (LangChain4jEmbeddingModel) model)
                .forEach(LangChain4jEmbeddingModel::shutdown);
    }

    private void register(Map<String, EmbeddingModel> byAlias, Map<String, EmbeddingModel> byId,
                          EmbeddingModelDefinition definition, ObjectMapper objectMapper) {
        EmbeddingModel model = createModel(definition, objectMapper);
        byAlias.put(definition.alias(), model);
        byId.putIfAbsent(model.modelId(), model);
    }

    private EmbeddingModel createModel(EmbeddingModelDefinition definition,
                                       ObjectMapper objectMapper) {
        String provider = normalizeProvider(definition.provider());
        return switch (provider) {
            case PROVIDER_DASHSCOPE, PROVIDER_OPENAI ->
                    new RemoteEmbeddingModel(definition, objectMapper);
            case PROVIDER_LOCAL -> new LangChain4jEmbeddingModel(definition);
            case PROVIDER_DETERMINISTIC -> new DeterministicEmbeddingModel(definition);
            default -> throw new IllegalStateException("不支持的 embedding provider: "
                    + definition.provider());
        };
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return PROVIDER_OPENAI;
        }
        return provider.trim().toLowerCase();
    }

    private EmbeddingModelDefinition legacyDefinition(String alias,
                                                      RagProperties.Embedding embedding) {
        String provider = normalizeProvider(embedding.getProvider());
        return new EmbeddingModelDefinition(alias, provider, embedding.getModelId(),
                Math.max(1, embedding.getDimension()),
                defaultMaxInputTokens(embedding.getMaxInputTokens(), provider),
                Math.max(1, embedding.getBatchSize()), BigDecimal.ZERO, null, null, null,
                DEFAULT_TIMEOUT_MS, Math.max(0, embedding.getMaxRetries()),
                Math.max(0L, embedding.getRetryBackoffMs()),
                Math.max(1, embedding.getThreads()));
    }

    private EmbeddingModelDefinition definitionOf(String alias, RagProperties.ModelSettings settings,
                                                  RagProperties.Embedding embedding) {
        String provider = normalizeProvider(firstText(settings.getProvider(),
                embedding.getProvider()));
        String model = firstText(settings.getModel(), embedding.getModelId());
        int dimension = positive(settings.getDimension(), embedding.getDimension());
        int configuredMaxTokens = !ObjectUtils.isEmpty(settings.getMaxInputTokens())
                && settings.getMaxInputTokens() > 0
                ? settings.getMaxInputTokens() : embedding.getMaxInputTokens();
        int maxInputTokens = defaultMaxInputTokens(configuredMaxTokens, provider);
        int batch = positive(settings.getMaxBatchSize(), embedding.getBatchSize());
        if (PROVIDER_DASHSCOPE.equals(provider) && batch > DASHSCOPE_MAX_BATCH) {
            LOG.warn("embedding 模型 '{}' 的 max-batch-size={} 超过 DashScope 上限 {}，已收敛",
                    alias, batch, DASHSCOPE_MAX_BATCH);
            batch = DASHSCOPE_MAX_BATCH;
        }
        String apiKey = StringUtils.hasText(settings.getApiKey()) ? settings.getApiKey()
                : resolveEnv(settings.getApiKeyEnv());
        return new EmbeddingModelDefinition(alias, provider, model, dimension, maxInputTokens,
                batch, ObjectUtils.isEmpty(settings.getInputPerMillion())
                        ? BigDecimal.ZERO : settings.getInputPerMillion(),
                settings.getApiBase(), apiKey, settings.getApiKeyEnv(),
                positive(settings.getTimeoutMs(), DEFAULT_TIMEOUT_MS),
                nonNegative(settings.getMaxRetries(), embedding.getMaxRetries()),
                nonNegative(settings.getRetryBackoffMs(), embedding.getRetryBackoffMs()),
                positive(settings.getThreads(), embedding.getThreads()));
    }

    private int defaultMaxInputTokens(int configured, String provider) {
        if (configured > 0) {
            return configured;
        }
        if (PROVIDER_LOCAL.equals(provider)) {
            return DEFAULT_LOCAL_MAX_INPUT_TOKENS;
        }
        return DEFAULT_REMOTE_MAX_INPUT_TOKENS;
    }

    private String resolveEnv(String apiKeyEnv) {
        return StringUtils.hasText(apiKeyEnv) ? System.getenv(apiKeyEnv) : null;
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private int positive(Integer value, int fallback) {
        return !ObjectUtils.isEmpty(value) && value > 0 ? value : Math.max(1, fallback);
    }

    private int nonNegative(Integer value, int fallback) {
        return !ObjectUtils.isEmpty(value) && value >= 0 ? value : Math.max(0, fallback);
    }

    private long nonNegative(Long value, long fallback) {
        return !ObjectUtils.isEmpty(value) && value >= 0 ? value : Math.max(0L, fallback);
    }
}
