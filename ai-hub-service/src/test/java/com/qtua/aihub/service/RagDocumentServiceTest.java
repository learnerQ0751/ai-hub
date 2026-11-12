package com.qtua.aihub.service;

import com.qtua.aihub.api.model.res.RagDocumentDeleteResDTO;
import com.qtua.aihub.api.model.res.RagDocumentListResDTO;
import com.qtua.aihub.api.model.res.RagDocumentResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.core.rag.RagDeleteResult;
import com.qtua.aihub.core.rag.RagDocumentCore;
import com.qtua.aihub.core.rag.RagIngestResult;
import com.qtua.aihub.integration.rag.DocumentParseException;
import com.qtua.aihub.manager.model.RagDocumentBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-3.1 服务层测试：参数校验、解析失败映射、去重响应、列表上限与删除 404。
 */
@ExtendWith(MockitoExtension.class)
class RagDocumentServiceTest {

    @Mock
    private RagDocumentCore documentCore;

    @Mock
    private EmbeddingModel embeddingModel;

    private RagProperties properties;
    private RagDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.getUpload().setMaxSizeBytes(16);
        properties.getList().setDefaultLimit(10);
        properties.getList().setMaxLimit(20);
        service = new RagDocumentServiceImpl(documentCore, embeddingModel, properties);
    }

    @Test
    void ingest_rejects_blank_project() {
        assertThatThrownBy(() -> service.ingest(" ", "a.txt", "text/plain", bytes("x")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException error = (ChatCompletionException) ex;
                    assertThat(error.getMessage()).contains("project_id");
                    assertThat(error.getParam()).isEqualTo("project_id");
                    assertThat(error.getStatus().value()).isEqualTo(400);
                });
    }

    @Test
    void ingest_uses_type_specific_upload_limit() {
        properties.getUpload().getTypeLimits().put("txt", 4);

        assertThatThrownBy(() -> service.ingest("p1", "a.txt", "text/plain", bytes("12345")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException error = (ChatCompletionException) ex;
                    assertThat(error.getMessage()).contains("4").contains("txt");
                    assertThat(error.getParam()).isEqualTo("file");
                });
    }

    @Test
    void ingest_rejects_oversize_file() {
        assertThatThrownBy(() -> service.ingest("p1", "a.txt", "text/plain",
                bytes("01234567890123456789")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getMessage())
                        .contains("文件大小"));
    }

    @Test
    void ingest_maps_parse_failure_to_chinese_400() {
        when(documentCore.ingest(anyString(), anyString(), anyString(), any(byte[].class),
                anyBoolean())).thenThrow(new DocumentParseException("PDF 文件解析失败"));

        assertThatThrownBy(() -> service.ingest("p1", "a.pdf", "application/pdf", bytes("%PDF")))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException error = (ChatCompletionException) ex;
                    assertThat(error.getType()).isEqualTo("invalid_request_error");
                    assertThat(error.getParam()).isEqualTo("file");
                    assertThat(error.getCode()).isEqualTo("400");
                    assertThat(error.getMessage()).contains("PDF");
                });
    }

    @Test
    void ingest_returns_duplicated_document() {
        RagDocumentBO document = document("d1", "p1", "b16");
        when(documentCore.ingest(eq("p1"), eq("a.md"), eq("text/markdown"),
                any(byte[].class), eq(false)))
                .thenReturn(new RagIngestResult(document, true, false));
        when(documentCore.find("d1", "p1")).thenReturn(Optional.of(document));

        RagDocumentResDTO result = service.ingest("p1", "a.md", "text/markdown", bytes("hello"));

        assertThat(result.duplicated()).isTrue();
        assertThat(result.docId()).isEqualTo("d1");
        assertThat(result.indexStatus()).isEqualTo("PENDING");
    }

    @Test
    void list_uses_default_limit_and_maps_documents() {
        when(documentCore.list("p1", 10)).thenReturn(List.of(document("d1", "p1", "b16")));

        RagDocumentListResDTO result = service.list("p1", null);

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.documents().get(0).docId()).isEqualTo("d1");
    }

    @Test
    void list_clamps_limit_to_max() {
        when(documentCore.list("p1", 20)).thenReturn(List.of());

        RagDocumentListResDTO result = service.list("p1", 999);

        assertThat(result.count()).isZero();
        verify(documentCore).list("p1", 20);
    }

    @Test
    void list_marks_reindex_required_when_embedding_model_changed() {
        RagDocumentBO document = document("d1", "p1", "b16");
        RagDocumentBO indexed = new RagDocumentBO(document.docId(), document.projectId(),
                document.source(), document.contentType(), document.sizeBytes(),
                document.contentHash(), null, document.chunkCount(), "READY", "model-a", 384,
                10L, BigDecimal.ZERO, null, document.createdAt(), document.updatedAt());
        when(documentCore.list("p1", 10)).thenReturn(List.of(indexed));
        when(embeddingModel.modelId()).thenReturn("model-b");

        RagDocumentListResDTO result = service.list("p1", null);

        assertThat(result.documents().get(0).reindexRequired()).isTrue();
    }

    @Test
    void reindex_returns_updated_document() {
        RagDocumentBO document = document("d1", "p1", "b16");
        RagDocumentBO indexed = new RagDocumentBO(document.docId(), document.projectId(),
                document.source(), document.contentType(), document.sizeBytes(),
                document.contentHash(), null, document.chunkCount(), "READY", "model-b", 384,
                20L, BigDecimal.ZERO, null, document.createdAt(), document.updatedAt());
        when(documentCore.find("d1", "p1")).thenReturn(Optional.of(document));
        when(documentCore.reindex("d1")).thenReturn(Optional.of(indexed));
        when(embeddingModel.modelId()).thenReturn("model-b");
        when(embeddingModel.dimension()).thenReturn(384);

        RagDocumentResDTO result = service.reindex("d1", "p1");

        assertThat(result.indexStatus()).isEqualTo("READY");
        assertThat(result.reindexRequired()).isFalse();
        assertThat(result.embeddingModel()).isEqualTo("model-b");
    }

    @Test
    void reindex_unknown_document_returns_404() {
        when(documentCore.find("d1", "p1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reindex("d1", "p1"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getStatus().value())
                        .isEqualTo(404));
    }

    @Test
    void remove_returns_404_for_unknown_or_cross_project_document() {
        when(documentCore.find("d1", "p2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove("d1", "p2"))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> assertThat(((ChatCompletionException) ex).getStatus().value())
                        .isEqualTo(404));
    }

    @Test
    void remove_cascades_and_returns_counts() {
        RagDocumentBO document = document("d1", "p1", "b16");
        when(documentCore.find("d1", "p1")).thenReturn(Optional.of(document));
        when(documentCore.delete("d1")).thenReturn(new RagDeleteResult(true, 3, 3));

        RagDocumentDeleteResDTO result = service.remove("d1", "p1");

        assertThat(result.deleted()).isTrue();
        assertThat(result.chunksDeleted()).isEqualTo(3);
        assertThat(result.vectorsDeleted()).isEqualTo(3);
    }

    private RagDocumentBO document(String docId, String projectId, String source) {
        Instant now = Instant.now();
        return new RagDocumentBO(docId, projectId, source, "text/markdown", 5L, "hash", null,
                3, "PENDING", null, null, null, BigDecimal.ZERO, null, now, now);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
