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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-5.3 混合检索单元测试：RRF 融合、权重、降级与开关。
 */
class RagHybridRetrievalTest {

    private static final String PROJECT = "project-a";
    private static final String MODEL = "model-a";
    private static final String QUERY = "年假 报销";

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
    void fuses_two_paths_by_rrf_and_honors_weights() {
        when(vectorStore.search(any())).thenReturn(List.of(
                match("c1", "d1", 0.9), match("c2", "d1", 0.8)));
        when(chunkManager.listByChunkIdsInTenant(eq("default"), anyList())).thenReturn(List.of(
                chunk("c1", "d1", "年假", 0), chunk("c2", "d1", "年假 报销", 1)));
        when(chunkManager.searchKeyword(eq("default"), eq(PROJECT), anyList(), anyInt()))
                .thenReturn(List.of(chunk("c2", "d1", "年假 报销", 1),
                        chunk("c1", "d1", "年假", 0)));

        properties.getSearch().setRrfVectorWeight(0.0);
        properties.getSearch().setRrfKeywordWeight(2.0);
        RagRetrievalResult keywordFirst = core.retrieve(request(2, 10, 0.0));
        assertThat(keywordFirst.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c2", "c1");
        assertThat(keywordFirst.hits()).allSatisfy(hit -> {
            assertThat(hit.matchType()).isEqualTo("both");
            assertThat(hit.rrfScore()).isNotNull();
            assertThat(hit.vectorScore()).isNotNull();
            assertThat(hit.keywordScore()).isNotNull();
        });
        assertThat(keywordFirst.reranked()).isFalse();

        properties.getSearch().setRrfVectorWeight(1.0);
        properties.getSearch().setRrfKeywordWeight(0.0);
        RagRetrievalResult vectorFirst = core.retrieve(request(2, 10, 0.0));
        assertThat(vectorFirst.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1", "c2");
    }

    @Test
    void vector_failure_falls_back_to_keyword_result() {
        when(embeddingModel.embedWithUsage(any(), any()))
                .thenThrow(new IllegalStateException("vector down"));
        when(chunkManager.searchKeyword(eq("default"), eq(PROJECT), anyList(), anyInt()))
                .thenReturn(List.of(chunk("c2", "d1", "年假 报销", 1)));

        RagRetrievalResult result = core.retrieve(request(2, 10, 0.0));

        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c2");
        assertThat(result.hits().get(0).matchType()).isEqualTo("keyword");
        assertThat(result.hits().get(0).vectorScore()).isNull();
        assertThat(result.embeddingTokens()).isZero();
    }

    @Test
    void keyword_failure_falls_back_to_vector_result() {
        when(vectorStore.search(any())).thenReturn(List.of(match("c1", "d1", 0.9)));
        when(chunkManager.listByChunkIdsInTenant(eq("default"), anyList())).thenReturn(List.of(
                chunk("c1", "d1", "年假", 0)));
        when(chunkManager.searchKeyword(anyString(), anyString(), anyList(), anyInt()))
                .thenThrow(new IllegalStateException("keyword down"));

        RagRetrievalResult result = core.retrieve(request(2, 10, 0.0));

        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1");
        assertThat(result.hits().get(0).matchType()).isEqualTo("vector");
        assertThat(result.hits().get(0).vectorScore()).isNotNull();
    }

    @Test
    void keyword_path_is_skipped_when_switch_is_off() {
        properties.getSearch().setHybridEnabled(false);
        when(vectorStore.search(any())).thenReturn(List.of(match("c1", "d1", 0.9)));
        when(chunkManager.listByChunkIdsInTenant(eq("default"), anyList())).thenReturn(List.of(
                chunk("c1", "d1", "年假", 0)));

        RagRetrievalResult result = core.retrieve(request(2, 10, 0.0));

        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c1");
        assertThat(result.hits().get(0).matchType()).isEqualTo("vector");
        assertThat(result.hits().get(0).rrfScore()).isNull();
        verify(chunkManager, never()).searchKeyword(anyString(), anyString(), anyList(), anyInt());
    }

    @Test
    void min_score_filters_only_vector_path() {
        when(vectorStore.search(any())).thenReturn(List.of());
        when(chunkManager.searchKeyword(eq("default"), eq(PROJECT), anyList(), anyInt()))
                .thenReturn(List.of(chunk("c2", "d1", "年假 报销", 1)));

        RagRetrievalResult result = core.retrieve(request(2, 10, 0.9));

        ArgumentCaptor<VectorSearchQuery> query = ArgumentCaptor.forClass(VectorSearchQuery.class);
        verify(vectorStore).search(query.capture());
        assertThat(query.getValue().minScore()).isEqualTo(0.9);
        assertThat(result.hits()).extracting(RagRetrievedChunk::chunkId)
                .containsExactly("c2");
        assertThat(result.hits().get(0).matchType()).isEqualTo("keyword");
    }

    private RagSearchRequest request(int topK, int candidateLimit, double minScore) {
        return new RagSearchRequest(PROJECT, QUERY, topK, minScore, RagSearchFilter.empty(),
                true, candidateLimit);
    }

    private VectorMatch match(String chunkId, String docId, double score) {
        return new VectorMatch(chunkId, docId, PROJECT, MODEL, score);
    }

    private RagChunkBO chunk(String chunkId, String docId, String content, int index) {
        return new RagChunkBO(chunkId, docId, PROJECT, index, content, content.length(), "demo.md",
                "READY", MODEL, 2, null, Instant.now());
    }
}
