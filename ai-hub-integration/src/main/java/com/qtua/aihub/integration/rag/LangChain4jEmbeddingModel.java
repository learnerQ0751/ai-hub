package com.qtua.aihub.integration.rag;

import com.qtua.aihub.common.rag.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LangChain4j 本地 embedding 适配（默认 all-minilm-l6-v2）。
 *
 * <p>ONNX 模型常驻内存，使用专用有界线程池（显式 core/max/队列/拒绝策略），
 * 提供 {@link #shutdown()} 在目录销毁时关闭。模型维度与配置必须一致；不一致启动失败，
 * 避免写入维度不匹配的向量。</p>
 */
public class LangChain4jEmbeddingModel implements EmbeddingModel {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jEmbeddingModel.class);

    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 64;
    private static final long SHUTDOWN_WAIT_SECONDS = 5L;

    private final AllMiniLmL6V2EmbeddingModel delegate;
    private final ThreadPoolExecutor executor;
    private final String modelId;
    private final int dimension;
    private final int maxInputTokens;

    /**
     * 构造本地模型。
     *
     * @param definition 模型配置
     */
    public LangChain4jEmbeddingModel(EmbeddingModelDefinition definition) {
        int threads = Math.max(1, definition.threads());
        this.executor = new ThreadPoolExecutor(threads, threads, KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                threadFactory(definition.alias()), new ThreadPoolExecutor.CallerRunsPolicy());
        this.delegate = new AllMiniLmL6V2EmbeddingModel(executor);
        this.modelId = definition.model();
        this.dimension = delegate.dimension();
        this.maxInputTokens = definition.maxInputTokens();
        if (dimension != definition.dimension()) {
            shutdown();
            throw new IllegalStateException("RAG embedding dimension mismatch: configured="
                    + definition.dimension() + ", model=" + dimension);
        }
        LOG.info("local embedding model loaded: alias={}, modelId={}, dimension={}, threads={}, "
                        + "maxInputTokens={}",
                definition.alias(), modelId, dimension, threads, maxInputTokens);
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
     * 本地模型无费用。
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
     * @return 向量
     */
    @Override
    public float[] embed(String text) {
        Embedding embedding = delegate.embed(text).content();
        return copyAndValidate(embedding.vector());
    }

    /**
     * 批量文本向量化。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        List<Embedding> embeddings = delegate.embedAll(segments).content();
        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException("embedding result size mismatch");
        }
        return embeddings.stream().map(embedding -> copyAndValidate(embedding.vector())).toList();
    }

    /**
     * 关闭专用线程池。
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private float[] copyAndValidate(float[] vector) {
        if (vector.length != dimension) {
            throw new IllegalStateException("embedding dimension mismatch");
        }
        return vector.clone();
    }

    private static ThreadFactory threadFactory(String alias) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "rag-embedding-" + alias + "-"
                    + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
