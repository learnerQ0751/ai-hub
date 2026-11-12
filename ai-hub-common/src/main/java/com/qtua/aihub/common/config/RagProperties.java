package com.qtua.aihub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG 配置（gateway.rag.*）。
 *
 * <p>上传大小、切分参数、去重策略、embedding 模型目录与向量存储选择。embedding 目录
 * 默认配置阿里 DashScope text-embedding-v3；旧单模型字段保留用于测试/离线确定性实现。</p>
 */
@ConfigurationProperties(prefix = "gateway.rag")
public class RagProperties {

    private static final int DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024;
    /** 0 表示按 embedding 模型窗口自动推导。 */
    private static final int DEFAULT_MAX_CHARS = 0;
    private static final int DEFAULT_OVERLAP_CHARS = 100;
    private static final int DEFAULT_PERSIST_BATCH_SIZE = 200;
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int DEFAULT_MAX_LIST_LIMIT = 500;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 384;
    private static final int DEFAULT_BATCH_SIZE = 32;
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long DEFAULT_RETRY_BACKOFF_MS = 200L;
    private static final int DEFAULT_EMBEDDING_THREADS = 2;
    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_MAX_TOP_K = 50;
    private static final double DEFAULT_MIN_SCORE = 0.0d;
    private static final int DEFAULT_CANDIDATE_MULTIPLIER = 4;
    private static final int DEFAULT_MAX_CANDIDATES = 200;
    private static final int DEFAULT_MAX_QUERY_CHARS = 2000;
    private static final double DEFAULT_RERANK_LEXICAL_WEIGHT = 0.3d;
    private static final int DEFAULT_MAX_FILTER_DOC_IDS = 100;
    private static final int DEFAULT_ANSWER_TOP_K = 4;
    private static final int DEFAULT_MAX_INPUTS = 2048;
    private static final int DEFAULT_MAX_CONTEXT_CHARS = 6000;

    /** 上传配置。 */
    private Upload upload = new Upload();
    /** 切分配置。 */
    private Chunk chunk = new Chunk();
    /** 去重配置。 */
    private Dedup dedup = new Dedup();
    /** 列表配置。 */
    private ListSettings list = new ListSettings();
    /** embedding 配置。 */
    private Embedding embedding = new Embedding();
    /** 向量存储配置。 */
    private VectorStoreSettings vectorStore = new VectorStoreSettings();
    /** 检索配置。 */
    private Search search = new Search();
    /** 生成配置。 */
    private Answer answer = new Answer();

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public Dedup getDedup() {
        return dedup;
    }

    public void setDedup(Dedup dedup) {
        this.dedup = dedup;
    }

    public ListSettings getList() {
        return list;
    }

    public void setList(ListSettings list) {
        this.list = list;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public VectorStoreSettings getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStoreSettings vectorStore) {
        this.vectorStore = vectorStore;
    }

    public Search getSearch() {
        return search;
    }

    public void setSearch(Search search) {
        this.search = search;
    }

    public Answer getAnswer() {
        return answer;
    }

    public void setAnswer(Answer answer) {
        this.answer = answer;
    }

    /**
     * 上传配置。
     */
    public static class Upload {

        /** 默认单文件最大字节数；未命中类型上限时使用，同时受 Spring multipart 硬上限约束。 */
        private int maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        /** 扩展名（小写，不含点）→ 最大字节数；覆盖默认上限。 */
        private Map<String, Integer> typeLimits = new LinkedHashMap<>();

        public int getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(int maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
        }

        public Map<String, Integer> getTypeLimits() {
            return typeLimits;
        }

        public void setTypeLimits(Map<String, Integer> typeLimits) {
            this.typeLimits = typeLimits;
        }
    }

    /**
     * 切分配置。
     */
    public static class Chunk {

        /** 单切片最大字符数。 */
        private int maxChars = DEFAULT_MAX_CHARS;
        /** 超长段落切分时的重叠字符数。 */
        private int overlapChars = DEFAULT_OVERLAP_CHARS;
        /** 切片落库每批条数。 */
        private int persistBatchSize = DEFAULT_PERSIST_BATCH_SIZE;
        /** 切片超出 embedding 模型安全窗口时的策略：warn（默认）或 fail（启动失败）。 */
        private String overflowPolicy = "warn";

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }

        public int getOverlapChars() {
            return overlapChars;
        }

        public void setOverlapChars(int overlapChars) {
            this.overlapChars = overlapChars;
        }

        public int getPersistBatchSize() {
            return persistBatchSize;
        }

        public void setPersistBatchSize(int persistBatchSize) {
            this.persistBatchSize = persistBatchSize;
        }

        public String getOverflowPolicy() {
            return overflowPolicy;
        }

        public void setOverflowPolicy(String overflowPolicy) {
            this.overflowPolicy = overflowPolicy;
        }
    }

    /**
     * 去重配置。
     */
    public static class Dedup {

        /** 重复 hash 时是否覆盖旧文档；默认 false（返回已有文档）。 */
        private boolean override;

        public boolean isOverride() {
            return override;
        }

        public void setOverride(boolean override) {
            this.override = override;
        }
    }

    /**
     * 列表配置。
     */
    public static class ListSettings {

        /** 默认返回条数。 */
        private int defaultLimit = DEFAULT_LIST_LIMIT;
        /** 最大返回条数。 */
        private int maxLimit = DEFAULT_MAX_LIST_LIMIT;

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }
    }

    /**
     * embedding 配置。
     */
    public static class Embedding {

        /** RAG 使用的模型别名；models 为空时作为旧单模型配置的别名。 */
        private String model = "default";
        /** 模型别名 → 模型配置；为空时回退到旧单模型字段。 */
        private Map<String, ModelSettings> models = new LinkedHashMap<>();
        /** 单次 /v1/embeddings 请求允许的最大输入条数，防止超大请求。 */
        private int maxInputs = DEFAULT_MAX_INPUTS;
        /** 旧单模型：提供方：langchain4j-local / deterministic / dashscope / openai-compatible。 */
        private String provider = "langchain4j-local";
        /** 旧单模型：模型标识（含版本），写入索引元数据。 */
        private String modelId = "all-minilm-l6-v2";
        /** 旧单模型：向量维度。 */
        private int dimension = DEFAULT_EMBEDDING_DIMENSION;
        /** 旧单模型：最大输入 token；0 表示按 provider 推断。 */
        private int maxInputTokens;
        /** 旧单模型：每批向量化条数。 */
        private int batchSize = DEFAULT_BATCH_SIZE;
        /** 旧单模型：批次失败最大重试次数。 */
        private int maxRetries = DEFAULT_MAX_RETRIES;
        /** 旧单模型：重试退避基数（毫秒），按次数线性放大。 */
        private long retryBackoffMs = DEFAULT_RETRY_BACKOFF_MS;
        /** 旧单模型：本地模型专用线程数。 */
        private int threads = DEFAULT_EMBEDDING_THREADS;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Map<String, ModelSettings> getModels() {
            return models;
        }

        public void setModels(Map<String, ModelSettings> models) {
            this.models = models;
        }

        public int getMaxInputs() {
            return maxInputs;
        }

        public void setMaxInputs(int maxInputs) {
            this.maxInputs = maxInputs;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public int getMaxInputTokens() {
            return maxInputTokens;
        }

        public void setMaxInputTokens(int maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }
    }

    /**
     * 单个 embedding 模型配置。
     */
    public static class ModelSettings {

        /** 提供方：dashscope / openai-compatible / langchain4j-local / deterministic。 */
        private String provider;
        /** 上游真实模型名。 */
        private String model;
        /** 向量维度。 */
        private Integer dimension;
        /** 单条文本最大输入 token。 */
        private Integer maxInputTokens;
        /** 单次请求最大条数（供应商批量上限）。 */
        private Integer maxBatchSize;
        /** 输入单价（每 1M tokens）。 */
        private BigDecimal inputPerMillion;
        /** OpenAI 兼容 base-url，不含 /embeddings。 */
        private String apiBase;
        /** API Key 明文/占位；禁止入库。 */
        private String apiKey;
        /** API Key 环境变量名；apiKey 为空时读取。 */
        private String apiKeyEnv;
        /** 连接/读超时（毫秒）。 */
        private Integer timeoutMs;
        /** 批次失败最大重试次数。 */
        private Integer maxRetries;
        /** 重试退避基数（毫秒）。 */
        private Long retryBackoffMs;
        /** 本地模型专用线程数。 */
        private Integer threads;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public Integer getMaxInputTokens() {
            return maxInputTokens;
        }

        public void setMaxInputTokens(Integer maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
        }

        public Integer getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(Integer maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }

        public BigDecimal getInputPerMillion() {
            return inputPerMillion;
        }

        public void setInputPerMillion(BigDecimal inputPerMillion) {
            this.inputPerMillion = inputPerMillion;
        }

        public String getApiBase() {
            return apiBase;
        }

        public void setApiBase(String apiBase) {
            this.apiBase = apiBase;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public Integer getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Integer timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(Long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public Integer getThreads() {
            return threads;
        }

        public void setThreads(Integer threads) {
            this.threads = threads;
        }
    }

    /**
     * 向量存储配置。
     */
    public static class VectorStoreSettings {

        /** 实现类型：pgvector（默认）或 memory（测试/无 PG）。 */
        private String type = "pgvector";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * 检索配置。
     */
    public static class Search {

        /** 默认返回条数。 */
        private int defaultTopK = DEFAULT_TOP_K;
        /** 最大返回条数；超过收敛。 */
        private int maxTopK = DEFAULT_MAX_TOP_K;
        /** 默认最小相似度。 */
        private double defaultMinScore = DEFAULT_MIN_SCORE;
        /** 向量召回放大倍数：candidate = topK × multiplier。 */
        private int candidateMultiplier = DEFAULT_CANDIDATE_MULTIPLIER;
        /** 候选上限，避免放大召回占用过多内存与 SQL 参数。 */
        private int maxCandidates = DEFAULT_MAX_CANDIDATES;
        /** 查询文本最大字符数。 */
        private int maxQueryChars = DEFAULT_MAX_QUERY_CHARS;
        /** 服务端重排总开关。 */
        private boolean rerankEnabled = true;
        /** 规则重排中词项覆盖率的权重（0~1）。 */
        private double rerankLexicalWeight = DEFAULT_RERANK_LEXICAL_WEIGHT;
        /** 元数据过滤允许的文档 ID 上限。 */
        private int maxFilterDocIds = DEFAULT_MAX_FILTER_DOC_IDS;

        public int getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(int defaultTopK) {
            this.defaultTopK = defaultTopK;
        }

        public int getMaxTopK() {
            return maxTopK;
        }

        public void setMaxTopK(int maxTopK) {
            this.maxTopK = maxTopK;
        }

        public double getDefaultMinScore() {
            return defaultMinScore;
        }

        public void setDefaultMinScore(double defaultMinScore) {
            this.defaultMinScore = defaultMinScore;
        }

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int candidateMultiplier) {
            this.candidateMultiplier = candidateMultiplier;
        }

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
        }

        public int getMaxQueryChars() {
            return maxQueryChars;
        }

        public void setMaxQueryChars(int maxQueryChars) {
            this.maxQueryChars = maxQueryChars;
        }

        public boolean isRerankEnabled() {
            return rerankEnabled;
        }

        public void setRerankEnabled(boolean rerankEnabled) {
            this.rerankEnabled = rerankEnabled;
        }

        public double getRerankLexicalWeight() {
            return rerankLexicalWeight;
        }

        public void setRerankLexicalWeight(double rerankLexicalWeight) {
            this.rerankLexicalWeight = rerankLexicalWeight;
        }

        public int getMaxFilterDocIds() {
            return maxFilterDocIds;
        }

        public void setMaxFilterDocIds(int maxFilterDocIds) {
            this.maxFilterDocIds = maxFilterDocIds;
        }
    }

    /**
     * 生成配置。
     */
    public static class Answer {

        /** 未显式指定时用于检索的条数。 */
        private int defaultTopK = DEFAULT_ANSWER_TOP_K;
        /** 放入 prompt 的资料部分最大字符数。 */
        private int maxContextChars = DEFAULT_MAX_CONTEXT_CHARS;
        /** 是否允许无召回时自由回答；默认关闭。 */
        private boolean allowFreeAnswer;
        /** 无召回时的兜底话术。 */
        private String fallbackMessage = "未在知识库中检索到相关资料，无法回答该问题。";
        /** 模型返回空内容时的兜底话术。 */
        private String emptyAnswerMessage = "模型未返回有效答案，请补充资料后重试。";

        public int getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(int defaultTopK) {
            this.defaultTopK = defaultTopK;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }

        public boolean isAllowFreeAnswer() {
            return allowFreeAnswer;
        }

        public void setAllowFreeAnswer(boolean allowFreeAnswer) {
            this.allowFreeAnswer = allowFreeAnswer;
        }

        public String getFallbackMessage() {
            return fallbackMessage;
        }

        public void setFallbackMessage(String fallbackMessage) {
            this.fallbackMessage = fallbackMessage;
        }

        public String getEmptyAnswerMessage() {
            return emptyAnswerMessage;
        }

        public void setEmptyAnswerMessage(String emptyAnswerMessage) {
            this.emptyAnswerMessage = emptyAnswerMessage;
        }
    }
}
