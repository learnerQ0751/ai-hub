package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingResult;
import com.qtua.aihub.common.rag.RagSearchFilter;
import com.qtua.aihub.common.rag.RagSearchRequest;
import com.qtua.aihub.common.rag.VectorMatch;
import com.qtua.aihub.common.rag.VectorSearchQuery;
import com.qtua.aihub.common.rag.VectorStore;
import com.qtua.aihub.manager.model.RagChunkBO;
import com.qtua.aihub.manager.rag.RagChunkManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-3.3 检索编排测试：候选放大、模型/项目下推、切片补齐、元数据过滤、重排与截断。
 */
class RagRetrievalCoreTest {

    private static final String PROJECT = "project-a";
    private static final String MODEL = "model-a";

    private EmbeddingModel embeddingModel;
    private VectorStore vectorStore;
    private RagChunkManager chunkManager;
    private RagProperties properties;
    private RagRetrievalCore core;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        vectorStore = mock(VectorStore.class);
        chunkManager = mock(RagChunkManager.class);
        properties = new RagProperties();
        properties.getSearch().setRerankLexicalWeight(0.5);
        lenient().when(embeddingModel.modelId()).thenReturn(MODEL);
        lenient().when(embeddingModel.dimension()).thenReturn(2);
        lenient().when(embeddingModel.embedWithUsage(any(), any())).thenAnswer(invocation ->
                new EmbeddingResult(List.of(new float[]{1.0f, 0.0f}), 3L));
        core = new RagRetrievalCore(embeddingModel, vectorStore, chunkManager,
                new RagLexicalReranker(), properties);
    }

    @Test
    void search_pushes_project_model_topk_and_threshold() {
        when(vectorStore.search(any())).thenReturn(List.of(
                match("c1", "d1", 0.9), match("c2", "d1", 0.8), match("c3", "d2", 0.7)));
        when(chunkManager.listByChunkIds(anyList())).thenReturn(List.of(
                chunk("c1", "d1", "demo.md", "年假 报销", 0, 0), chunk("c2", "d1", "demo.md",
                        "报销流程", 1, 0), chunk("c3", "d2", "other.md", "其他内容", 0, 0)));

        RagRetrievalResult result = core.retrieve(request(2, 10, 0.5, null, false));

        ArgumentCaptor<VectorSearchQuery> query = ArgumentCaptor.forClass(VectorSearchQuery.class);
        verify(vectorStore).search(query.capture());
        assertThat(query.getValue().projectId()).isEqualTo(PROJECT);
        assertThat(query.getValue().modelId()).isEqualTo(MODEL);
        assertThat(query.getValue().topK()).isEqualTo(10);
        assertThat(query.getValue().minScore()).isEqualTo(0.5);
        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1", "c2");
        assertThat(result.candidateCount()).isEqualTo(3);
        assertThat(result.reranked()).isFalse();
    }

    @Test
    void filters_by_doc_ids_and_source() {
        when(vectorStore.search(any())).thenReturn(List.of(
                match("c1", "d1", 0.9), match("c2", "d2", 0.8), match("c3", "d1", 0.7)));
        when(chunkManager.listByChunkIds(anyList())).thenReturn(List.of(
                chunk("c1", "d1", "a.md", "内容 A", 0, 0),
                chunk("c2", "d2", "b.md", "内容 B", 0, 0),
                chunk("c3", "d1", "b.md", "内容 C", 1, 0)));

        RagRetrievalResult result = core.retrieve(request(5, 10, 0.0,
                new RagSearchFilter(List.of("d1"), "b.md"), false));

        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c3");
    }

    @Test
    void rerank_reorders_candidates_when_enabled() {
        when(vectorStore.search(any())).thenReturn(List.of(
                match("c1", "d1", 0.6), match("c2", "d1", 0.55)));
        when(chunkManager.listByChunkIds(anyList())).thenReturn(List.of(
                chunk("c1", "d1", "demo.md", "报销流程说明", 0, 0),
                chunk("c2", "d1", "demo.md", "年假与报销制度", 1, 0)));

        RagRetrievalResult result = core.retrieve(request(2, 10, 0.0, null, true));

        assertThat(result.reranked()).isTrue();
        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c2", "c1");
        assertThat(result.hits().get(0).rerankScore()).isNotNull();
    }

    @Test
    void rerank_disabled_keeps_vector_score_order() {
        when(vectorStore.search(any())).thenReturn(List.of(
                match("c1", "d1", 0.6), match("c2", "d1", 0.55)));
        when(chunkManager.listByChunkIds(anyList())).thenReturn(List.of(
                chunk("c1", "d1", "demo.md", "报销流程说明", 0, 0),
                chunk("c2", "d1", "demo.md", "年假与报销制度", 1, 0)));

        RagRetrievalResult result = core.retrieve(request(2, 10, 0.0, null, false));

        assertThat(result.reranked()).isFalse();
        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1", "c2");
        assertThat(result.hits()).allSatisfy(hit -> assertThat(hit.rerankScore()).isNull());
    }

    @Test
    void rerank_without_query_terms_keeps_order_and_reports_not_reranked() {
        when(vectorStore.search(any())).thenReturn(List.of(
                match("c1", "d1", 0.6), match("c2", "d1", 0.55)));
        when(chunkManager.listByChunkIds(anyList())).thenReturn(List.of(
                chunk("c1", "d1", "demo.md", "内容一", 0, 0),
                chunk("c2", "d1", "demo.md", "内容二", 1, 0)));

        RagRetrievalResult result = core.retrieve(new RagSearchRequest(PROJECT, "？？", 2,
                0.0, RagSearchFilter.empty(), true, 10));

        assertThat(result.reranked()).isFalse();
        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1", "c2");
    }

    @Test
    void missing_chunk_is_skipped() {
        when(vectorStore.search(any())).thenReturn(List.of(
                match("c1", "d1", 0.9), match("c2", "d1", 0.8)));
        when(chunkManager.listByChunkIds(anyList())).thenReturn(List.of(
                chunk("c1", "d1", "demo.md", "年假 报销", 0, 0)));

        RagRetrievalResult result = core.retrieve(request(5, 10, 0.0, null, false));

        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1");
    }

    @Test
    void no_matches_returns_empty_result() {
        when(vectorStore.search(any())).thenReturn(List.of());

        RagRetrievalResult result = core.retrieve(request(5, 10, 0.0, null, false));

        assertThat(result.hits()).isEmpty();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.noHit()).isTrue();
        verify(chunkManager, times(0)).listByChunkIds(anyList());
    }

    @Test
    void chunk_lookup_is_batched_by_100() {
        List<VectorMatch> matches = new ArrayList<>();
        for (int index = 0; index < 250; index++) {
            matches.add(match("c" + index, "d1", 0.5));
        }
        when(vectorStore.search(any())).thenReturn(matches);
        when(chunkManager.listByChunkIds(anyList())).thenReturn(List.of());

        core.retrieve(request(5, 250, 0.0, null, false));

        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(chunkManager, times(3)).listByChunkIds(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(100, 100, 50);
    }

    @Test
    void candidate_limit_is_never_less_than_topk() {
        when(vectorStore.search(any())).thenReturn(List.of());

        core.retrieve(request(8, 3, 0.0, null, false));

        ArgumentCaptor<VectorSearchQuery> query = ArgumentCaptor.forClass(VectorSearchQuery.class);
        verify(vectorStore).search(query.capture());
        assertThat(query.getValue().topK()).isEqualTo(8);
    }

    private RagSearchRequest request(int topK, int candidateLimit, double minScore,
                                     RagSearchFilter filter, boolean rerank) {
        return new RagSearchRequest(PROJECT, "年假 报销", topK, minScore,
                filter == null ? RagSearchFilter.empty() : filter, rerank, candidateLimit);
    }

    private VectorMatch match(String chunkId, String docId, double score) {
        return new VectorMatch(chunkId, docId, PROJECT, MODEL, score);
    }

    private RagChunkBO chunk(String chunkId, String docId, String source, String content,
                             int index, int scoreIndex) {
        return new RagChunkBO(chunkId, docId, PROJECT, index, content, content.length(), source,
                "READY", MODEL, 2, null, Instant.now());
    }
}
