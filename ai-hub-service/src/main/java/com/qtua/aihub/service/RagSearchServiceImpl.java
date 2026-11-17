package com.qtua.aihub.service;

import com.qtua.aihub.api.RagSearchService;
import com.qtua.aihub.api.model.req.RagSearchFilterDTO;
import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.RagSearchHitResDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.rag.RagSearchFilter;
import com.qtua.aihub.common.rag.RagSearchRequest;
import com.qtua.aihub.common.tenant.TenantScope;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.common.trace.TraceEvent;
import com.qtua.aihub.common.trace.TraceRecorder;
import com.qtua.aihub.core.rag.RagRetrievalCore;
import com.qtua.aihub.core.rag.RagRetrievalResult;
import com.qtua.aihub.core.rag.RagRetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * RAG 检索服务实现。
 *
 * <p>职责：参数校验、默认值与上限收敛、服务端重排开关、DTO↔BO 与检索日志；
 * 召回/过滤/重排编排在 {@link RagRetrievalCore}。日志记录 query 与命中 chunk_id/score，
 * 不记录切片正文。</p>
 */
@Service
public class RagSearchServiceImpl implements RagSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(RagSearchServiceImpl.class);

    private static final int MAX_PROJECT_LENGTH = 64;
    private static final int MAX_SOURCE_LENGTH = 512;
    private static final double MIN_SIMILARITY = -1.0d;
    private static final double MAX_SIMILARITY = 1.0d;
    private static final int LOG_QUERY_MAX_CHARS = 200;
    private static final int TRACE_MAX_ITEMS = 10;
    private static final int HTTP_OK = 200;
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_PROJECT = "project_id";
    private static final String PARAM_TOP_K = "top_k";
    private static final String PARAM_MIN_SCORE = "min_score";
    private static final String PARAM_DOC_IDS = "filters.doc_ids";
    private static final String PARAM_SOURCE = "filters.source";

    private final RagRetrievalCore retrievalCore;
    private final RagProperties properties;
    private final TenantScopeGuard tenantScopeGuard;
    private final TraceRecorder traceRecorder;

    /**
     * 兼容构造器（单元测试）：使用无上下文的作用域守卫。
     *
     * @param retrievalCore 检索编排
     * @param properties    RAG 配置
     */
    public RagSearchServiceImpl(RagRetrievalCore retrievalCore, RagProperties properties) {
        this(retrievalCore, properties, new TenantScopeGuard(), TraceRecorder.NOOP);
    }

    /**
     * 构造服务。
     *
     * @param retrievalCore    检索编排
     * @param properties       RAG 配置
     * @param tenantScopeGuard 租户作用域守卫
     */
    @org.springframework.beans.factory.annotation.Autowired
    public RagSearchServiceImpl(RagRetrievalCore retrievalCore, RagProperties properties,
                                TenantScopeGuard tenantScopeGuard,
                                TraceRecorder traceRecorder) {
        this.retrievalCore = retrievalCore;
        this.properties = properties;
        this.tenantScopeGuard = tenantScopeGuard;
        this.traceRecorder = traceRecorder;
    }

    /**
     * 执行检索。
     *
     * @param reqDTO 检索请求
     * @return 命中结果
     */
    @Override
    public RagSearchResDTO search(RagSearchReqDTO reqDTO) {
        if (Objects.isNull(reqDTO)) {
            throw ChatCompletionException.invalidRequest("请求体不能为空", null);
        }
        TenantScope scope = tenantScopeGuard.resolveProject(reqDTO.projectId());
        String projectId = requireProject(scope.projectId());
        String query = requireQuery(reqDTO.query());
        int topK = resolveTopK(reqDTO.topK());
        double minScore = resolveMinScore(reqDTO.minScore());
        RagSearchFilter filter = resolveFilter(reqDTO.filters());
        boolean rerank = resolveRerank(reqDTO.rerank());
        int candidateLimit = resolveCandidateLimit(topK);
        LOG.info("rag search requested: projectId={}, query={}, topK={}, minScore={}, "
                        + "candidateLimit={}, rerank={}, filterDocIds={}, filterSource={}",
                projectId, truncateQuery(query), topK, minScore, candidateLimit, rerank,
                filter.docIds().size(), filter.source());
        RagRetrievalResult result = retrievalCore.retrieve(new RagSearchRequest(projectId, query,
                topK, minScore, filter, rerank, candidateLimit, scope.tenantId()));
        List<RagSearchHitResDTO> hits = result.hits().stream().map(this::toHit).toList();
        LOG.info("rag search completed: projectId={}, candidates={}, count={}, noHit={}, "
                        + "reranked={}",
                projectId, result.candidateCount(), hits.size(), hits.isEmpty(),
                result.reranked());
        recordTrace(scope, query, topK, result, hits);
        return new RagSearchResDTO(query, projectId, hits.size(), hits.isEmpty(),
                result.reranked(), hits, result.embeddingTokens(), result.embeddingModel());
    }

    private String requireProject(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw ChatCompletionException.invalidRequest("project_id 不能为空", PARAM_PROJECT);
        }
        String trimmed = projectId.trim();
        if (trimmed.length() > MAX_PROJECT_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    "project_id 长度不能超过 " + MAX_PROJECT_LENGTH + " 个字符", PARAM_PROJECT);
        }
        return trimmed;
    }

    private String requireQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw ChatCompletionException.invalidRequest("query 不能为空", PARAM_QUERY);
        }
        String trimmed = query.trim();
        int maxChars = Math.max(1, properties.getSearch().getMaxQueryChars());
        if (trimmed.length() > maxChars) {
            throw ChatCompletionException.invalidRequest(
                    "query 长度不能超过 " + maxChars + " 个字符", PARAM_QUERY);
        }
        return trimmed;
    }

    private int resolveTopK(Integer topK) {
        RagProperties.Search search = properties.getSearch();
        int maxTopK = Math.max(1, search.getMaxTopK());
        if (ObjectUtils.isEmpty(topK)) {
            return Math.max(1, Math.min(search.getDefaultTopK(), maxTopK));
        }
        if (topK <= 0) {
            throw ChatCompletionException.invalidRequest("top_k 必须大于 0", PARAM_TOP_K);
        }
        return Math.min(topK, maxTopK);
    }

    private double resolveMinScore(Double minScore) {
        if (ObjectUtils.isEmpty(minScore)) {
            return properties.getSearch().getDefaultMinScore();
        }
        if (minScore < MIN_SIMILARITY || minScore > MAX_SIMILARITY) {
            throw ChatCompletionException.invalidRequest(
                    "min_score 必须在 [" + MIN_SIMILARITY + ", " + MAX_SIMILARITY + "] 之间",
                    PARAM_MIN_SCORE);
        }
        return minScore;
    }

    private RagSearchFilter resolveFilter(RagSearchFilterDTO filterDTO) {
        if (Objects.isNull(filterDTO)) {
            return RagSearchFilter.empty();
        }
        List<String> docIds = normalizeDocIds(filterDTO.docIds());
        String source = normalizeSource(filterDTO.source());
        return new RagSearchFilter(docIds, source);
    }

    private List<String> normalizeDocIds(List<String> docIds) {
        if (ObjectUtils.isEmpty(docIds)) {
            return List.of();
        }
        int maxDocIds = Math.max(1, properties.getSearch().getMaxFilterDocIds());
        if (docIds.size() > maxDocIds) {
            throw ChatCompletionException.invalidRequest(
                    "filters.doc_ids 不能超过 " + maxDocIds + " 个", PARAM_DOC_IDS);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>(docIds.size());
        for (String docId : docIds) {
            if (!StringUtils.hasText(docId)) {
                throw ChatCompletionException.invalidRequest(
                        "filters.doc_ids 不能包含空值", PARAM_DOC_IDS);
            }
            normalized.add(docId.trim());
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String trimmed = source.trim();
        if (trimmed.length() > MAX_SOURCE_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    "filters.source 长度不能超过 " + MAX_SOURCE_LENGTH + " 个字符", PARAM_SOURCE);
        }
        return trimmed;
    }

    private boolean resolveRerank(Boolean requested) {
        // 服务端总开关优先：调用方只能关闭，不能绕过成本/延迟控制打开。
        return properties.getSearch().isRerankEnabled() && !Boolean.FALSE.equals(requested);
    }

    private int resolveCandidateLimit(int topK) {
        RagProperties.Search search = properties.getSearch();
        int multiplier = Math.max(1, search.getCandidateMultiplier());
        int maxCandidates = Math.max(1, search.getMaxCandidates());
        long amplified = (long) topK * multiplier;
        int clamped = (int) Math.min((long) maxCandidates, amplified);
        return Math.max(topK, clamped);
    }

    /**
     * 记录 RAG 召回分段（只记 id/score 与摘要，不记正文）。
     *
     * @param scope  作用域
     * @param query  查询
     * @param topK   topK
     * @param result 检索结果
     * @param hits   命中
     */
    private void recordTrace(TenantScope scope, String query, int topK,
                             RagRetrievalResult result, List<RagSearchHitResDTO> hits) {
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("queryChars", query.length());
            payload.put("topK", topK);
            payload.put("candidates", result.candidateCount());
            payload.put("noHit", hits.isEmpty());
            payload.put("chunkIds", hits.stream().limit(TRACE_MAX_ITEMS).map(RagSearchHitResDTO::chunkId).toList());
            payload.put("scores", hits.stream().limit(TRACE_MAX_ITEMS).map(RagSearchHitResDTO::score).toList());
            payload.put("matchTypes", hits.stream().limit(TRACE_MAX_ITEMS)
                    .map(RagSearchHitResDTO::matchType).toList());
            payload.put("rrfScores", hits.stream().limit(TRACE_MAX_ITEMS)
                    .map(RagSearchHitResDTO::rrfScore).toList());
            payload.put("embeddingTokens", result.embeddingTokens());
            traceRecorder.record(new TraceEvent(
                    traceId(), scope.tenantId(), scope.projectId(), keyHash(), "rag",
                    "search_knowledge", HTTP_OK, 0L, payload, java.time.Instant.now()));
        } catch (RuntimeException ex) {
            // Trace 失败不影响检索。
        }
    }

    private String traceId() {
        String traceId = org.slf4j.MDC.get("trace_id");
        return ObjectUtils.isEmpty(traceId) ? "-" : traceId;
    }

    private String keyHash() {
        com.qtua.aihub.common.tenant.TenantIdentity identity =
                tenantScopeGuard.currentIdentity();
        return ObjectUtils.isEmpty(identity) ? null : identity.keyHash();
    }

    private RagSearchHitResDTO toHit(RagRetrievedChunk chunk) {
        return new RagSearchHitResDTO(chunk.chunkId(), chunk.docId(), chunk.source(),
                chunk.chunkIndex(), chunk.content(), chunk.score(), chunk.rerankScore(),
                chunk.matchType(), chunk.vectorScore(), chunk.keywordScore(), chunk.rrfScore());
    }

    private String truncateQuery(String query) {
        return query.length() > LOG_QUERY_MAX_CHARS
                ? query.substring(0, LOG_QUERY_MAX_CHARS) + "..." : query;
    }
}
