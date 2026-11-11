package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.integration.rag.DocumentParseException;
import com.qtua.aihub.integration.rag.DocumentTextExtractor;
import com.qtua.aihub.manager.model.RagChunkBO;
import com.qtua.aihub.manager.model.RagDocumentBO;
import com.qtua.aihub.manager.rag.RagChunkManager;
import com.qtua.aihub.manager.rag.RagDocumentManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-3.1 接入编排测试：去重、解析失败留存、切片元数据与删除级联。
 */
class RagDocumentCoreTest {

    private RagDocumentManager documentManager;
    private RagChunkManager chunkManager;
    private DocumentTextExtractor textExtractor;
    private RagDocumentCore core;

    @BeforeEach
    void setUp() {
        documentManager = mock(RagDocumentManager.class);
        chunkManager = mock(RagChunkManager.class);
        textExtractor = mock(DocumentTextExtractor.class);
        RagProperties properties = new RagProperties();
        properties.getChunk().setMaxChars(40);
        properties.getChunk().setOverlapChars(5);
        core = new RagDocumentCore(documentManager, chunkManager,
                new RagChunker(properties), textExtractor, properties);
    }

    @Test
    void ingest_saves_document_and_chunks_with_metadata() {
        byte[] content = bytes("段落一\n\n段落二");
        when(documentManager.findByProjectAndHash(eq("p1"), anyString())).thenReturn(Optional.empty());
        when(textExtractor.extract("a.md", content)).thenReturn("段落一\n\n段落二");
        when(documentManager.findByDocId(anyString())).thenReturn(Optional.empty());

        RagIngestResult result = core.ingest("p1", "a.md", "text/markdown", content, false);

        ArgumentCaptor<RagDocumentBO> documentCaptor = ArgumentCaptor.forClass(RagDocumentBO.class);
        verify(documentManager).insert(documentCaptor.capture());
        RagDocumentBO document = documentCaptor.getValue();
        assertThat(document.projectId()).isEqualTo("p1");
        assertThat(document.source()).isEqualTo("a.md");
        assertThat(document.indexStatus()).isEqualTo("PENDING");
        assertThat(document.chunkCount()).isEqualTo(1);
        assertThat(document.rawContent()).isEqualTo(content);
        assertThat(result.duplicated()).isFalse();
        assertThat(result.overridden()).isFalse();

        ArgumentCaptor<List<RagChunkBO>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkManager).insertAll(chunkCaptor.capture(), anyInt());
        RagChunkBO chunk = chunkCaptor.getValue().get(0);
        assertThat(chunk.docId()).isEqualTo(document.docId());
        assertThat(chunk.chunkId()).startsWith(document.docId());
        assertThat(chunk.projectId()).isEqualTo("p1");
        assertThat(chunk.source()).isEqualTo("a.md");
        assertThat(chunk.indexStatus()).isEqualTo("PENDING");
        assertThat(chunk.createdAt()).isNotNull();
    }

    @Test
    void parse_failure_persists_failed_document_and_raw_content() {
        byte[] content = bytes("broken");
        when(documentManager.findByProjectAndHash(eq("p1"), anyString())).thenReturn(Optional.empty());
        when(textExtractor.extract("broken.pdf", content))
                .thenThrow(new DocumentParseException("PDF 文件解析失败"));

        assertThatThrownBy(() -> core.ingest("p1", "broken.pdf", "application/pdf", content, false))
                .isInstanceOf(DocumentParseException.class);

        ArgumentCaptor<RagDocumentBO> captor = ArgumentCaptor.forClass(RagDocumentBO.class);
        verify(documentManager).insert(captor.capture());
        RagDocumentBO document = captor.getValue();
        assertThat(document.indexStatus()).isEqualTo("FAILED");
        assertThat(document.chunkCount()).isZero();
        assertThat(document.rawContent()).isEqualTo(content);
        assertThat(document.errorMessage()).contains("PDF");
        verify(chunkManager, never()).insertAll(anyList(), anyInt());
    }

    @Test
    void duplicate_returns_existing_without_reparsing() {
        RagDocumentBO existing = document("d-existing", "READY");
        when(documentManager.findByProjectAndHash(eq("p1"), anyString()))
                .thenReturn(Optional.of(existing));

        RagIngestResult result = core.ingest("p1", "a.md", "text/markdown", bytes("same"),
                false);

        assertThat(result.duplicated()).isTrue();
        assertThat(result.document().docId()).isEqualTo("d-existing");
        verify(textExtractor, never()).extract(anyString(), any(byte[].class));
        verify(documentManager, never()).insert(any());
    }

    @Test
    void override_deletes_existing_before_reingest() {
        RagDocumentBO existing = document("d-existing", "READY");
        byte[] content = bytes("new content");
        when(documentManager.findByProjectAndHash(eq("p1"), anyString()))
                .thenReturn(Optional.of(existing));
        when(textExtractor.extract("a.md", content)).thenReturn("new content");
        when(documentManager.findByDocId(anyString())).thenReturn(Optional.empty());

        RagIngestResult result = core.ingest("p1", "a.md", "text/markdown", content, true);

        assertThat(result.overridden()).isTrue();
        verify(chunkManager).deleteByDocId("d-existing");
        verify(documentManager).deleteByDocId("d-existing");
        verify(documentManager).insert(any());
    }

    @Test
    void failed_document_is_reingested_without_override() {
        RagDocumentBO existing = document("d-failed", "FAILED");
        byte[] content = bytes("retry content");
        when(documentManager.findByProjectAndHash(eq("p1"), anyString()))
                .thenReturn(Optional.of(existing));
        when(textExtractor.extract("a.txt", content)).thenReturn("retry content");
        when(documentManager.findByDocId(anyString())).thenReturn(Optional.empty());

        RagIngestResult result = core.ingest("p1", "a.txt", "text/plain", content, false);

        assertThat(result.duplicated()).isFalse();
        verify(documentManager).deleteByDocId("d-failed");
        verify(documentManager).insert(any());
    }

    @Test
    void delete_cascades_chunks_and_document() {
        when(chunkManager.deleteByDocId("d1")).thenReturn(3);
        when(documentManager.deleteByDocId("d1")).thenReturn(1);

        RagDeleteResult result = core.delete("d1");

        assertThat(result.deleted()).isTrue();
        assertThat(result.chunksDeleted()).isEqualTo(3);
    }

    @Test
    void sha256_and_chunk_id_are_deterministic() {
        byte[] content = bytes("abc");

        assertThat(core.sha256(content)).hasSize(64).isEqualTo(core.sha256(content));
        assertThat(core.chunkId("doc-1", 2)).isEqualTo("doc-1-0002");
    }

    private RagDocumentBO document(String docId, String status) {
        Instant now = Instant.now();
        return new RagDocumentBO(docId, "p1", "a.md", "text/markdown", 4L, "hash", null, 1,
                status, null, null, null, java.math.BigDecimal.ZERO, null, now, now);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
