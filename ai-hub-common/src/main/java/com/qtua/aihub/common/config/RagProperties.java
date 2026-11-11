package com.qtua.aihub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置（gateway.rag.*）。
 *
 * <p>上传大小、切分参数、去重策略、embedding 与向量存储选择。默认本地
 * all-minilm-l6-v2（384 维）+ PostgreSQL pgvector；测试通过配置切换为确定性
 * embedding 与内存向量存储。</p>
 */
@ConfigurationProperties(prefix = "gateway.rag")
public class RagProperties {

    private static final int DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024;
    private static final int DEFAULT_MAX_CHARS = 800;
    private static final int DEFAULT_OVERLAP_CHARS = 100;
    private static final int DEFAULT_PERSIST_BATCH_SIZE = 200;
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int DEFAULT_MAX_LIST_LIMIT = 500;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 384;
    private static final int DEFAULT_BATCH_SIZE = 32;
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long DEFAULT_RETRY_BACKOFF_MS = 200L;
    private static final int DEFAULT_EMBEDDING_THREADS = 2;

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

    /**
     * 上传配置。
     */
    public static class Upload {

        /** 单文件最大字节数；同时需与 Spring multipart 上限匹配。 */
        private int maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;

        public int getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(int maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
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

        /** 提供方：langchain4j-local（默认）或 deterministic（测试/离线）。 */
        private String provider = "langchain4j-local";
        /** 模型标识（含版本），写入索引元数据。 */
        private String modelId = "all-minilm-l6-v2";
        /** 向量维度；默认模型固定 384。 */
        private int dimension = DEFAULT_EMBEDDING_DIMENSION;
        /** 每批向量化条数。 */
        private int batchSize = DEFAULT_BATCH_SIZE;
        /** 批次失败最大重试次数。 */
        private int maxRetries = DEFAULT_MAX_RETRIES;
        /** 重试退避基数（毫秒），按次数线性放大。 */
        private long retryBackoffMs = DEFAULT_RETRY_BACKOFF_MS;
        /** 本地模型专用线程数。 */
        private int threads = DEFAULT_EMBEDDING_THREADS;

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
}
