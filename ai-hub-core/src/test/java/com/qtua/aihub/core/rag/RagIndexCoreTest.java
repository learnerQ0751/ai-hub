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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-3.2 索引编排测试：分批、重试、状态流转、失败可重试与 reindex 清理旧向量。
 */
class RagIndexCoreTest {

    private RagDocumentManager documentManager;
    private RagChunkManager chunkManager;
    private EmbeddingModel embeddingModel;
    private VectorStore vectorStore;
    private RagProperties properties;
    private RagIndexCore core;

    @BeforeEach
    void setUp() {
        documentManager = mock(RagDocumentManager.class);
        chunkManager = mock(RagChunkManager.class);
        embeddingModel = mock(EmbeddingModel.class);
        vectorStore = mock(VectorStore.class);
        properties = new RagProperties();
        properties.getEmbedding().setBatchSize(2);
        properties.getEmbedding().setMaxRetries(1);
        properties.getEmbedding().setRetryBackoffMs(0L);
        properties.getEmbedding().setDimension(2);
        lenient().when(embeddingModel.modelId()).thenReturn("model-a");
        lenient().when(embeddingModel.dimension()).thenReturn(2);
        lenient().when(embeddingModel.costOfInputTokens(anyLong())).thenReturn(BigDecimal.ZERO);
        core = new RagIndexCore(documentManager, chunkManager, embeddingModel, vectorStore,
                properties);
        lenient().when(documentManager.findByDocId("d1"))
                .thenReturn(Optional.of(document("d1")));
        lenient().when(chunkManager.listByDocId("d1")).thenReturn(List.of(chunk("c1"),
                chunk("c2"), chunk("c3")));
    }

    @Test
    void index_batches_embeddings_and_marks_ready() {
        when(embeddingModel.embedAllWithUsage(anyList(), any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return new EmbeddingResult(
                    texts.stream().map(text -> new float[]{1.0f, 0.0f}).toList(), 1L);
        });

        core.index("d1");

        verify(embeddingModel, times(2)).embedAllWithUsage(anyList(), any());
        ArgumentCaptor<List<VectorRecord>> records = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).upsertAll(records.capture());
        assertThat(records.getAllValues().stream().flatMap(List::stream).count()).isEqualTo(3);
        assertThat(records.getAllValues().stream().flatMap(List::stream)
                .map(VectorRecord::modelId).distinct()).containsExactly("model-a");
        verify(documentManager).updateStatus("d1", RagIndexStatus.PROCESSING.name(), null);
        verify(chunkManager).updateStatusByDocId("d1", RagIndexStatus.PROCESSING.name(), null);
        verify(chunkManager).updateStatusByDocId("d1", RagIndexStatus.READY.name(), null);
        verify(documentManager).updateStatus("d1", RagIndexStatus.READY.name(), null);
        verify(documentManager).updateEmbeddingMeta(eq("d1"), eq("model-a"), eq(2),
                anyLong(), eq(BigDecimal.ZERO));
    }

    @Test
    void index_retries_failed_batch_then_succeeds() {
        when(embeddingModel.embedAllWithUsage(anyList(), any()))
                .thenThrow(new IllegalStateException("embedding unavailable"))
                .thenAnswer(invocation -> {
                    List<String> texts = invocation.getArgument(0);
                    return new EmbeddingResult(
                            texts.stream().map(text -> new float[]{1.0f, 0.0f}).toList(), 1L);
                });

        core.index("d1");

        verify(embeddingModel, times(3)).embedAllWithUsage(anyList(), any());
        verify(documentManager).updateStatus("d1", RagIndexStatus.READY.name(), null);
    }

    @Test
    void index_marks_failed_when_retries_exhausted_and_reindex_recovers() {
        when(embeddingModel.embedAllWithUsage(anyList(), any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenThrow(new IllegalStateException("boom"))
                .thenAnswer(invocation -> {
                    List<String> texts = invocation.getArgument(0);
                    return new EmbeddingResult(
                            texts.stream().map(text -> new float[]{1.0f, 0.0f}).toList(), 1L);
                });
        when(vectorStore.deleteByDocument("d1")).thenReturn(2);

        core.index("d1");

        verify(documentManager).updateStatus("d1", RagIndexStatus.FAILED.name(), "boom");
        verify(chunkManager).updateStatusByDocId("d1", RagIndexStatus.FAILED.name(), "boom");

        core.reindex("d1");

        verify(vectorStore).deleteByDocument("d1");
        verify(documentManager).updateStatus("d1", RagIndexStatus.READY.name(), null);
    }

    @Test
    void index_without_chunks_marks_failed() {
        when(chunkManager.listByDocId("d1")).thenReturn(List.of());

        core.index("d1");

        verify(documentManager).updateStatus("d1", RagIndexStatus.FAILED.name(),
                "文档没有可索引的切片");
        verify(embeddingModel, org.mockito.Mockito.never()).embedAllWithUsage(anyList(), any());
    }

    @Test
    void index_missing_document_returns_null() {
        when(documentManager.findByDocId("d-missing")).thenReturn(Optional.empty());

        assertThat(core.index("d-missing")).isNull();
    }

    private RagDocumentBO document(String docId) {
        Instant now = Instant.now();
        return new RagDocumentBO(docId, "p1", "a.md", "text/markdown", 10L, "hash", null, 3,
                RagIndexStatus.PENDING.name(), null, null, null, BigDecimal.ZERO, null, now, now);
    }

    private RagChunkBO chunk(String chunkId) {
        return new RagChunkBO(chunkId, "d1", "p1", 0, "content-" + chunkId, 10, "a.md",
                RagIndexStatus.PENDING.name(), null, null, null, Instant.now());
    }
}
