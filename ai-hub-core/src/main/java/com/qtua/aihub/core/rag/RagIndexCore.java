package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingResult;
import com.qtua.aihub.common.rag.RagIndexStatus;
import com.qtua.aihub.common.rag.VectorRecord;
import com.qtua.aihub.common.rag.VectorStore;
import com.qtua.aihub.manager.model.RagChunkBO;
import com.qtua.aihub.manager.model.RagDocumentBO;
import com.qtua.aihub.manager.rag.RagChunkManager;
import com.qtua.aihub.manager.rag.RagDocumentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RAG 切片向量化编排。
 *
 * <p>按 batch-size 分批调用 {@link EmbeddingModel#embedAll(List)}，失败按 max-retries 退避重试；
 * 成功后写 {@link VectorStore} 并把文档/切片置 READY，失败置 FAILED 并记录可读错误。
 * reindex 会先删除旧向量再重建，保证模型切换/维度隔离。本类无可变业务状态，多实例安全。</p>
 */
@Component
public class RagIndexCore {

    private static final Logger LOG = LoggerFactory.getLogger(RagIndexCore.class);

    private static final int MAX_ERROR_LENGTH = 1024;

    private final RagDocumentManager documentManager;
    private final RagChunkManager chunkManager;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final RagProperties properties;

    /**
     * 构造索引编排。
     *
     * @param documentManager 文档门面
     * @param chunkManager    切片门面
     * @param embeddingModel  向量化端口
     * @param vectorStore     向量存储端口
     * @param properties      RAG 配置
     */
    public RagIndexCore(RagDocumentManager documentManager, RagChunkManager chunkManager,
                        EmbeddingModel embeddingModel, VectorStore vectorStore,
                        RagProperties properties) {
        this.documentManager = documentManager;
        this.chunkManager = chunkManager;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * 构建（或重试）文档索引。
     *
     * @param docId 文档 ID
     * @return 索引后的文档；文档不存在返回 null
     */
    public RagDocumentBO index(String docId) {
        Optional<RagDocumentBO> found = documentManager.findByDocId(docId);
        if (found.isEmpty()) {
            return null;
        }
        RagDocumentBO document = found.get();
        int chunkCount = chunkManager.countByDocId(docId);
        if (chunkCount <= 0) {
            documentManager.updateStatus(docId, RagIndexStatus.FAILED.name(),
                    "文档没有可索引的切片");
            return documentManager.findByDocId(docId).orElse(document);
        }
        documentManager.updateStatus(docId, RagIndexStatus.PROCESSING.name(), null);
        chunkManager.updateStatusByDocId(docId, RagIndexStatus.PROCESSING.name(), null);
        long startedAt = System.currentTimeMillis();
        try {
            long inputTokens = embedChunks(docId, document.projectId());
            BigDecimal cost = embeddingCost(inputTokens);
            long durationMs = Math.max(0L, System.currentTimeMillis() - startedAt);
            finishReady(docId, durationMs, cost);
            LOG.info("rag index ready: docId={}, chunks={}, model={}, dimension={}, "
                            + "inputTokens={}, cost={}, durationMs={}",
                    docId, chunkCount, embeddingModel.modelId(),
                    embeddingModel.dimension(), inputTokens, cost, durationMs);
        } catch (RuntimeException ex) {
            markFailed(docId, ex);
        }
        return documentManager.findByDocId(docId).orElse(document);
    }

    /**
     * 重建索引：先删除旧向量，再按当前模型重新向量化。
     *
     * @param docId 文档 ID
     * @return 索引后的文档；文档不存在返回 null
     */
    public RagDocumentBO reindex(String docId) {
        if (documentManager.findByDocId(docId).isEmpty()) {
            return null;
        }
        int removed = vectorStore.deleteByDocument(docId);
        LOG.info("rag reindex removing old vectors: docId={}, removed={}", docId, removed);
        return index(docId);
    }

    /**
     * 游标分页加载并向量化（REQ-3.10）。
     *
     * <p>每页内存占用量为 pageSize 条切片；页内再按 embedding batch-size 拆分，保持原有
     * 重试粒度。最后一页不足 pageSize 时提前结束，避免多一次空查询。</p>
     *
     * @param docId     文档 ID
     * @param projectId 项目 ID
     * @return 输入 token 总数
     */
    private long embedChunks(String docId, String projectId) {
        int pageSize = resolvePageSize();
        long inputTokens = 0L;
        int cursor = -1;
        while (true) {
            List<RagChunkBO> page = chunkManager.listPageByDocId(docId, cursor, pageSize);
            if (page.isEmpty()) {
                break;
            }
            cursor = page.get(page.size() - 1).chunkIndex();
            inputTokens += embedPage(docId, projectId, page);
            if (page.size() < pageSize) {
                break;
            }
        }
        return inputTokens;
    }

    private int resolvePageSize() {
        int configured = properties.getIndex().getPageSize();
        return configured > 0 ? configured : Math.max(1, properties.getEmbedding().getBatchSize());
    }

    private long embedPage(String docId, String projectId, List<RagChunkBO> page) {
        int batchSize = Math.max(1, properties.getEmbedding().getBatchSize());
        long inputTokens = 0L;
        for (int start = 0; start < page.size(); start += batchSize) {
            int end = Math.min(start + batchSize, page.size());
            List<RagChunkBO> batch = page.subList(start, end);
            EmbeddingResult result = embedWithRetry(
                    batch.stream().map(RagChunkBO::content).toList());
            inputTokens += result.inputTokens();
            List<float[]> vectors = validate(result.vectors());
            List<VectorRecord> records = new ArrayList<>(batch.size());
            for (int index = 0; index < batch.size(); index++) {
                RagChunkBO chunk = batch.get(index);
                records.add(new VectorRecord(chunk.chunkId(), docId, projectId,
                        embeddingModel.modelId(), embeddingModel.dimension(), vectors.get(index),
                        Instant.now()));
            }
            vectorStore.upsertAll(records);
        }
        return inputTokens;
    }

    private EmbeddingResult embedWithRetry(List<String> texts) {
        int attempts = Math.max(1, properties.getEmbedding().getMaxRetries() + 1);
        RuntimeException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                EmbeddingResult result = embeddingModel.embedAllWithUsage(texts, null);
                if (Objects.isNull(result)) {
                    throw new IllegalStateException("embedding result is empty");
                }
                return result;
            } catch (RuntimeException ex) {
                last = ex;
                LOG.warn("rag embedding batch failed: attempt={}, size={}, reason={}",
                        attempt + 1, texts.size(), ex.getClass().getSimpleName());
                if (attempt + 1 < attempts) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw Objects.requireNonNull(last);
    }

    private List<float[]> validate(List<float[]> vectors) {
        if (Objects.isNull(vectors) || vectors.isEmpty()) {
            throw new IllegalStateException("embedding result is empty");
        }
        for (float[] vector : vectors) {
            if (Objects.isNull(vector) || vector.length != embeddingModel.dimension()) {
                throw new IllegalStateException("embedding dimension mismatch");
            }
        }
        return vectors;
    }

    private BigDecimal embeddingCost(long inputTokens) {
        if (inputTokens <= 0L) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = embeddingModel.costOfInputTokens(inputTokens);
        return Objects.isNull(cost) ? BigDecimal.ZERO : cost;
    }

    private void sleepBackoff(int attempt) {
        long backoff = Math.max(0L, properties.getEmbedding().getRetryBackoffMs())
                * (attempt + 1L);
        if (backoff <= 0L) {
            return;
        }
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding retry interrupted", ex);
        }
    }

    private void finishReady(String docId, long durationMs, BigDecimal cost) {
        chunkManager.updateEmbeddingMetaByDocId(docId, embeddingModel.modelId(),
                embeddingModel.dimension());
        chunkManager.updateStatusByDocId(docId, RagIndexStatus.READY.name(), null);
        documentManager.updateEmbeddingMeta(docId, embeddingModel.modelId(),
                embeddingModel.dimension(), durationMs, cost);
        documentManager.updateStatus(docId, RagIndexStatus.READY.name(), null);
    }

    private void markFailed(String docId, RuntimeException ex) {
        String message = Objects.isNull(ex.getMessage()) ? ex.getClass().getSimpleName()
                : ex.getMessage();
        String truncated = message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH) : message;
        chunkManager.updateStatusByDocId(docId, RagIndexStatus.FAILED.name(), truncated);
        documentManager.updateStatus(docId, RagIndexStatus.FAILED.name(), truncated);
        LOG.error("rag index failed: docId={}, type={}, messageLength={}",
                docId, ex.getClass().getSimpleName(), message.length());
    }
}
