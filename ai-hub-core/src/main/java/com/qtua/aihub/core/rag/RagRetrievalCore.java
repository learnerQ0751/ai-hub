package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingResult;
import com.qtua.aihub.common.rag.RagSearchRequest;
import com.qtua.aihub.common.rag.VectorMatch;
import com.qtua.aihub.common.rag.VectorSearchQuery;
import com.qtua.aihub.common.rag.VectorStore;
import com.qtua.aihub.manager.model.RagChunkBO;
import com.qtua.aihub.manager.rag.RagChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * RAG 检索编排。
 *
 * <p>流程：query 向量化 → 按 project + 当前模型放大召回 → 按 chunk_id 分批补齐切片 →
 * 元数据过滤 → 可选规则重排 → 截断 topK。候选放大用于抵消过滤/重排带来的淘汰，
 * 上限由 Service 收敛；本类无可变业务状态，多实例安全。</p>
 */
@Component
public class RagRetrievalCore {

    private static final Logger LOG = LoggerFactory.getLogger(RagRetrievalCore.class);

    private static final int CHUNK_BATCH_SIZE = 100;
    private static final int LOG_QUERY_MAX_CHARS = 200;
    private static final int LOG_HITS_MAX = 20;

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final RagChunkManager chunkManager;
    private final RagLexicalReranker reranker;
    private final RagProperties properties;

    /**
     * 构造检索编排。
     *
     * @param embeddingModel 向量化端口
     * @param vectorStore    向量存储端口
     * @param chunkManager   切片门面
     * @param reranker       规则重排
     * @param properties     RAG 配置（重排权重）
     */
    public RagRetrievalCore(EmbeddingModel embeddingModel, VectorStore vectorStore,
                            RagChunkManager chunkManager, RagLexicalReranker reranker,
                            RagProperties properties) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.chunkManager = chunkManager;
        this.reranker = reranker;
        this.properties = properties;
    }

    /**
     * 执行一次检索。
     *
     * @param request 已校验并收敛的检索参数
     * @return 命中、候选数与是否重排
     */
    public RagRetrievalResult retrieve(RagSearchRequest request) {
        long startedAt = System.currentTimeMillis();
        EmbeddingResult embedded = embeddingModel.embedWithUsage(request.query(), null);
        float[] queryVector = validateQueryVector(embedded.vectors());
        int candidateLimit = Math.max(request.topK(), request.candidateLimit());
        List<VectorMatch> matches = vectorStore.search(new VectorSearchQuery(request.projectId(),
                embeddingModel.modelId(), queryVector, candidateLimit, request.minScore()));
        Map<String, RagChunkBO> chunks = loadChunks(matches);
        List<RagRetrievedChunk> candidates = filter(matches, chunks, request);
        boolean reranked = request.rerank() && !reranker.terms(request.query()).isEmpty();
        List<RagRetrievedChunk> ordered = reranked
                ? reranker.rerank(request.query(), candidates,
                        properties.getSearch().getRerankLexicalWeight())
                : candidates;
        List<RagRetrievedChunk> hits = ordered.stream().limit(request.topK()).toList();
        LOG.info("rag retrieval: projectId={}, query={}, minScore={}, candidates={}, filtered={}, "
                        + "returned={}, noHit={}, reranked={}, latencyMs={}, hits={}",
                request.projectId(), truncateQuery(request.query()), request.minScore(),
                matches.size(), candidates.size(), hits.size(), hits.isEmpty(), reranked,
                Math.max(0L, System.currentTimeMillis() - startedAt), summarizeHits(hits));
        return new RagRetrievalResult(hits, matches.size(), reranked,
                embedded.inputTokens(), embeddingModel.modelId());
    }

    private float[] validateQueryVector(List<float[]> vectors) {
        if (Objects.isNull(vectors) || vectors.isEmpty() || Objects.isNull(vectors.get(0))) {
            throw new IllegalStateException("embedding result is empty");
        }
        float[] vector = vectors.get(0);
        if (vector.length != embeddingModel.dimension()) {
            throw new IllegalStateException("embedding dimension mismatch");
        }
        return vector;
    }

    private Map<String, RagChunkBO> loadChunks(List<VectorMatch> matches) {
        List<String> chunkIds = matches.stream().map(VectorMatch::chunkId).distinct().toList();
        Map<String, RagChunkBO> chunks = new HashMap<>();
        for (int start = 0; start < chunkIds.size(); start += CHUNK_BATCH_SIZE) {
            int end = Math.min(start + CHUNK_BATCH_SIZE, chunkIds.size());
            for (RagChunkBO chunk : chunkManager.listByChunkIds(chunkIds.subList(start, end))) {
                chunks.put(chunk.chunkId(), chunk);
            }
        }
        return chunks;
    }

    private List<RagRetrievedChunk> filter(List<VectorMatch> matches,
                                           Map<String, RagChunkBO> chunks,
                                           RagSearchRequest request) {
        List<RagRetrievedChunk> candidates = new ArrayList<>(matches.size());
        for (VectorMatch match : matches) {
            RagChunkBO chunk = chunks.get(match.chunkId());
            if (Objects.isNull(chunk)) {
                // 向量与切片短暂不一致（重建/删除中）：跳过而不是返回半条结果。
                continue;
            }
            // 双保险：即使向量表误写，也不返回其他项目的切片。
            if (!Objects.equals(chunk.projectId(), request.projectId())) {
                continue;
            }
            if (!request.filter().matches(chunk.docId(), chunk.source())) {
                continue;
            }
            candidates.add(new RagRetrievedChunk(chunk.chunkId(), chunk.docId(),
                    chunk.projectId(), chunk.source(), chunk.chunkIndex(), chunk.content(),
                    match.score(), null));
        }
        return candidates;
    }

    private String summarizeHits(List<RagRetrievedChunk> hits) {
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(hits.size(), LOG_HITS_MAX);
        for (int index = 0; index < limit; index++) {
            RagRetrievedChunk hit = hits.get(index);
            if (index > 0) {
                builder.append(", ");
            }
            double finalScore = Objects.isNull(hit.rerankScore()) ? hit.score() : hit.rerankScore();
            builder.append(hit.chunkId()).append(':')
                    .append(String.format(Locale.ROOT, "%.4f", finalScore));
        }
        if (hits.size() > LOG_HITS_MAX) {
            builder.append(", ... total=").append(hits.size());
        }
        return builder.append(']').toString();
    }

    private String truncateQuery(String query) {
        return query.length() > LOG_QUERY_MAX_CHARS
                ? query.substring(0, LOG_QUERY_MAX_CHARS) + "..." : query;
    }
}
