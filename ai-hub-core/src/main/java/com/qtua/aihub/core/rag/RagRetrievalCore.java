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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * RAG 检索编排。
 *
 * <p>三期路径：query 向量化 → 按 project + 当前模型放大召回 → 按 chunk_id 分批补齐切片 →
 * 元数据过滤 → 可选规则重排 → 截断 topK。</p>
 *
 * <p>REQ-5.3 混合检索：向量 + 字面两路召回，RRF 融合；任一路失败时另一路单独返回。
 * 关闭开关时回到三期路径。`min_score` 只作用于向量路，字面结果不因阈值被误杀。</p>
 */
@Component
public class RagRetrievalCore {

    private static final Logger LOG = LoggerFactory.getLogger(RagRetrievalCore.class);

    private static final int CHUNK_BATCH_SIZE = 100;
    private static final int LOG_QUERY_MAX_CHARS = 200;
    private static final int LOG_HITS_MAX = 20;
    private static final int MIN_TERM_LIMIT = 1;
    private static final int MIN_RRF_K = 1;
    private static final double MIN_WEIGHT = 0.0d;
    private static final String MATCH_VECTOR = "vector";
    private static final String MATCH_KEYWORD = "keyword";
    private static final String MATCH_BOTH = "both";

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
     * @param reranker       规则重排/词项提取
     * @param properties     RAG 配置
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
        RagProperties.Search search = properties.getSearch();
        Set<String> allTerms = reranker.terms(request.query());
        List<String> terms = limitTerms(allTerms, search.getKeywordMaxTerms());
        boolean hybrid = search.isHybridEnabled() && !terms.isEmpty();
        int candidateLimit = Math.max(request.topK(), request.candidateLimit());

        VectorPath vectorPath = null;
        RuntimeException vectorError = null;
        try {
            vectorPath = vectorCandidates(request, candidateLimit);
        } catch (RuntimeException ex) {
            if (!hybrid) {
                throw ex;
            }
            vectorError = ex;
            LOG.warn("hybrid vector path failed, fallback to keyword: projectId={}, error={}",
                    request.projectId(), ex.getMessage());
        }

        KeywordPath keywordPath = null;
        RuntimeException keywordError = null;
        if (hybrid) {
            try {
                keywordPath = keywordCandidates(request, allTerms, candidateLimit);
            } catch (RuntimeException ex) {
                keywordError = ex;
                LOG.warn("hybrid keyword path failed, fallback to vector: projectId={}, error={}",
                        request.projectId(), ex.getMessage());
            }
        }

        if (org.springframework.util.ObjectUtils.isEmpty(vectorPath)
                && org.springframework.util.ObjectUtils.isEmpty(keywordPath)) {
            throw Objects.isNull(vectorError) ? keywordError : vectorError;
        }

        RagRetrievalResult result;
        if (!hybrid) {
            result = phaseThreeResult(request, allTerms, vectorPath, startedAt);
        } else if (Objects.isNull(vectorPath)) {
            result = singlePathResult(keywordPath, true);
        } else if (Objects.isNull(keywordPath)) {
            result = singlePathResult(vectorPath, false);
        } else {
            result = fuse(vectorPath, keywordPath, request, search);
        }
        LOG.info("rag retrieval: projectId={}, query={}, hybrid={}, minScore={}, vectorCandidates={}, "
                        + "keywordCandidates={}, returned={}, noHit={}, reranked={}, latencyMs={}, "
                        + "hits={}",
                request.projectId(), truncateQuery(request.query()), hybrid, request.minScore(),
                Objects.isNull(vectorPath) ? 0 : vectorPath.candidates.size(),
                Objects.isNull(keywordPath) ? 0 : keywordPath.candidates.size(),
                result.hits().size(), result.hits().isEmpty(), result.reranked(),
                Math.max(0L, System.currentTimeMillis() - startedAt),
                summarizeHits(result.hits()));
        return result;
    }

    private RagRetrievalResult phaseThreeResult(RagSearchRequest request, Set<String> terms,
                                                VectorPath vectorPath, long startedAt) {
        boolean reranked = request.rerank() && !terms.isEmpty();
        List<RagRetrievedChunk> ordered = reranked
                ? reranker.rerank(request.query(), vectorPath.candidates,
                        properties.getSearch().getRerankLexicalWeight())
                : vectorPath.candidates;
        List<RagRetrievedChunk> hits = ordered.stream().limit(request.topK()).toList();
        return new RagRetrievalResult(hits, vectorPath.candidates.size(), reranked,
                vectorPath.embeddingTokens, vectorPath.embeddingModel);
    }

    private RagRetrievalResult singlePathResult(Object path, boolean keywordOnly) {
        List<RagRetrievedChunk> candidates;
        int candidateCount;
        long tokens;
        String model;
        if (keywordOnly) {
            KeywordPath keywordPath = (KeywordPath) path;
            candidates = keywordPath.candidates;
            candidateCount = keywordPath.candidateCount;
            tokens = 0L;
            model = null;
        } else {
            VectorPath vectorPath = (VectorPath) path;
            candidates = vectorPath.candidates;
            candidateCount = vectorPath.candidates.size();
            tokens = vectorPath.embeddingTokens;
            model = vectorPath.embeddingModel;
        }
        return new RagRetrievalResult(candidates, candidateCount, false, tokens, model);
    }

    private RagRetrievalResult fuse(VectorPath vectorPath, KeywordPath keywordPath,
                                    RagSearchRequest request, RagProperties.Search search) {
        Map<String, Integer> vectorRanks = new LinkedHashMap<>();
        for (int index = 0; index < vectorPath.candidates.size(); index++) {
            vectorRanks.put(vectorPath.candidates.get(index).chunkId(), index + 1);
        }
        Map<String, Integer> keywordRanks = new LinkedHashMap<>();
        for (int index = 0; index < keywordPath.candidates.size(); index++) {
            keywordRanks.put(keywordPath.candidates.get(index).chunkId(), index + 1);
        }
        Map<String, RagRetrievedChunk> vectorById = index(vectorPath.candidates);
        Map<String, RagRetrievedChunk> keywordById = index(keywordPath.candidates);
        Set<String> chunkIds = new LinkedHashSet<>();
        chunkIds.addAll(vectorById.keySet());
        chunkIds.addAll(keywordById.keySet());

        int rrfK = Math.max(MIN_RRF_K, search.getRrfK());
        double vectorWeight = Math.max(MIN_WEIGHT, search.getRrfVectorWeight());
        double keywordWeight = Math.max(MIN_WEIGHT, search.getRrfKeywordWeight());
        List<RagRetrievedChunk> fused = new ArrayList<>(chunkIds.size());
        for (String chunkId : chunkIds) {
            RagRetrievedChunk vectorHit = vectorById.get(chunkId);
            RagRetrievedChunk keywordHit = keywordById.get(chunkId);
            double rrfScore = 0.0d;
            if (!Objects.isNull(vectorHit)) {
                rrfScore += vectorWeight / (rrfK + vectorRanks.get(chunkId));
            }
            if (!Objects.isNull(keywordHit)) {
                rrfScore += keywordWeight / (rrfK + keywordRanks.get(chunkId));
            }
            RagRetrievedChunk base = Objects.isNull(vectorHit) ? keywordHit : vectorHit;
            String matchType = Objects.isNull(vectorHit) ? MATCH_KEYWORD
                    : (Objects.isNull(keywordHit) ? MATCH_VECTOR : MATCH_BOTH);
            fused.add(new RagRetrievedChunk(base.chunkId(), base.docId(), base.projectId(),
                    base.source(), base.chunkIndex(), base.content(),
                    Objects.isNull(vectorHit) ? 0.0d : vectorHit.score(), null, matchType,
                    Objects.isNull(vectorHit) ? null : vectorHit.score(),
                    Objects.isNull(keywordHit) ? null : keywordHit.keywordScore(), rrfScore));
        }
        fused.sort(Comparator.comparingDouble(RagRetrievedChunk::rrfScore).reversed()
                .thenComparing(RagRetrievedChunk::chunkId));
        List<RagRetrievedChunk> hits = fused.stream().limit(request.topK()).toList();
        return new RagRetrievalResult(hits, vectorPath.candidates.size() + keywordPath.candidateCount,
                false, vectorPath.embeddingTokens, vectorPath.embeddingModel);
    }

    private VectorPath vectorCandidates(RagSearchRequest request, int candidateLimit) {
        EmbeddingResult embedded = embeddingModel.embedWithUsage(request.query(), null);
        float[] queryVector = validateQueryVector(embedded.vectors());
        List<VectorMatch> matches = vectorStore.search(new VectorSearchQuery(request.projectId(),
                embeddingModel.modelId(), queryVector, candidateLimit, request.minScore(),
                request.tenantId()));
        Map<String, RagChunkBO> chunks = loadChunks(request.tenantId(), matches);
        return new VectorPath(filter(matches, chunks, request), embedded.inputTokens(),
                embeddingModel.modelId());
    }

    private KeywordPath keywordCandidates(RagSearchRequest request, Set<String> terms,
                                          int candidateLimit) {
        List<String> queryTerms = limitTerms(terms, properties.getSearch().getKeywordMaxTerms());
        List<RagChunkBO> rows = chunkManager.searchKeyword(request.tenantId(),
                request.projectId(), queryTerms, candidateLimit);
        List<RagRetrievedChunk> candidates = new ArrayList<>(rows.size());
        for (RagChunkBO chunk : rows) {
            if (!Objects.equals(chunk.projectId(), request.projectId())
                    || !Objects.equals(chunk.tenantId(), request.tenantId())
                    || !request.filter().matches(chunk.docId(), chunk.source())) {
                continue;
            }
            double coverage = reranker.coverage(terms, chunk.content());
            if (coverage <= 0.0d) {
                continue;
            }
            candidates.add(new RagRetrievedChunk(chunk.chunkId(), chunk.docId(),
                    chunk.projectId(), chunk.source(), chunk.chunkIndex(), chunk.content(),
                    0.0d, null, MATCH_KEYWORD, null, coverage, null));
        }
        candidates.sort(Comparator
                .comparingDouble(RagRetrievedChunk::keywordScore).reversed()
                .thenComparingInt(RagRetrievedChunk::chunkIndex)
                .thenComparing(RagRetrievedChunk::chunkId));
        return new KeywordPath(candidates, rows.size());
    }

    private Map<String, RagRetrievedChunk> index(List<RagRetrievedChunk> chunks) {
        Map<String, RagRetrievedChunk> indexed = new LinkedHashMap<>();
        for (RagRetrievedChunk chunk : chunks) {
            indexed.putIfAbsent(chunk.chunkId(), chunk);
        }
        return indexed;
    }

    private List<String> limitTerms(Set<String> terms, int maxTerms) {
        int limit = Math.max(MIN_TERM_LIMIT, maxTerms);
        return terms.stream().limit(limit).toList();
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

    private Map<String, RagChunkBO> loadChunks(String tenantId, List<VectorMatch> matches) {
        List<String> chunkIds = matches.stream().map(VectorMatch::chunkId).distinct().toList();
        Map<String, RagChunkBO> chunks = new HashMap<>();
        for (int start = 0; start < chunkIds.size(); start += CHUNK_BATCH_SIZE) {
            int end = Math.min(start + CHUNK_BATCH_SIZE, chunkIds.size());
            for (RagChunkBO chunk : chunkManager.listByChunkIdsInTenant(tenantId,
                    chunkIds.subList(start, end))) {
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
            if (!Objects.equals(chunk.projectId(), request.projectId())
                    || !Objects.equals(chunk.tenantId(), request.tenantId())) {
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
            double finalScore = !Objects.isNull(hit.rrfScore()) ? hit.rrfScore()
                    : (!Objects.isNull(hit.rerankScore()) ? hit.rerankScore() : hit.score());
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

    private record VectorPath(List<RagRetrievedChunk> candidates, long embeddingTokens,
                              String embeddingModel) {
    }

    private record KeywordPath(List<RagRetrievedChunk> candidates, int candidateCount) {
    }
}
