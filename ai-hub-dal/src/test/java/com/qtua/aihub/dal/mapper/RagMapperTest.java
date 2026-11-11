package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.RagChunkDO;
import com.qtua.aihub.dal.model.RagDocumentDO;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-3.1 rag_documents / rag_chunks Mapper 测试：原始文件、唯一 hash、列表与状态。
 */
@MybatisTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
@ContextConfiguration(classes = RagMapperTest.TestConfig.class)
@MapperScan("com.qtua.aihub.dal.mapper")
@Sql(scripts = {"/db/rag-schema.sql"})
class RagMapperTest {

    /**
     * DAL 模块没有启动类，提供一个测试用配置锚点。
     */
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    private RagDocumentMapper documentMapper;

    @Autowired
    private RagChunkMapper chunkMapper;

    @Test
    void inserts_and_reads_document_with_raw_content() {
        documentMapper.insert(document("d1", "p1", "hash-1", "hello".getBytes(StandardCharsets.UTF_8)));

        RagDocumentDO stored = documentMapper.selectByDocId("d1");

        assertThat(stored).isNotNull();
        assertThat(stored.getProjectId()).isEqualTo("p1");
        assertThat(stored.getSource()).isEqualTo("a.txt");
        assertThat(stored.getRawContent()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(stored.getIndexStatus()).isEqualTo("PENDING");
        assertThat(stored.getEmbeddingCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void duplicate_project_hash_is_rejected() {
        documentMapper.insert(document("d1", "p1", "hash-1", new byte[]{1}));

        assertThatThrownBy(() -> documentMapper.insert(
                document("d2", "p1", "hash-1", new byte[]{2})))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lists_documents_by_project_without_raw_content() {
        documentMapper.insert(document("d1", "p1", "hash-1", new byte[]{1}));
        documentMapper.insert(document("d2", "p2", "hash-1", new byte[]{2}));

        List<RagDocumentDO> documents = documentMapper.selectByProject("p1", 10);

        assertThat(documents).extracting(RagDocumentDO::getDocId).containsExactly("d1");
        assertThat(documents.get(0).getRawContent()).isNull();
        assertThat(documentMapper.selectByProjectAndHash("p1", "hash-1").getDocId())
                .isEqualTo("d1");
    }

    @Test
    void updates_status_chunk_count_and_embedding_meta() {
        documentMapper.insert(document("d1", "p1", "hash-1", new byte[]{1}));
        Instant now = Instant.now();

        assertThat(documentMapper.updateStatus("d1", "READY", null, now)).isEqualTo(1);
        assertThat(documentMapper.updateChunkCount("d1", 3, now)).isEqualTo(1);
        assertThat(documentMapper.updateEmbeddingMeta("d1", "model-a", 384, 12L,
                new BigDecimal("0.0001"), now)).isEqualTo(1);

        RagDocumentDO stored = documentMapper.selectByDocId("d1");
        assertThat(stored.getIndexStatus()).isEqualTo("READY");
        assertThat(stored.getChunkCount()).isEqualTo(3);
        assertThat(stored.getEmbeddingModel()).isEqualTo("model-a");
        assertThat(stored.getEmbeddingDimension()).isEqualTo(384);
        assertThat(stored.getEmbeddingDurationMs()).isEqualTo(12L);
        assertThat(stored.getEmbeddingCost()).isEqualByComparingTo("0.0001");
    }

    @Test
    void chunks_batch_insert_update_and_delete() {
        RagChunkDO first = chunk("d1-0000", "d1", 0, "第一段");
        RagChunkDO second = chunk("d1-0001", "d1", 1, "第二段");
        chunkMapper.insertBatch(List.of(first, second));

        List<RagChunkDO> chunks = chunkMapper.selectByDocId("d1");
        assertThat(chunks).extracting(RagChunkDO::getChunkId)
                .containsExactly("d1-0000", "d1-0001");
        assertThat(chunkMapper.countByDocId("d1")).isEqualTo(2);

        chunkMapper.updateStatusByDocId("d1", "READY", null);
        chunkMapper.updateEmbeddingMetaByDocId("d1", "model-a", 384);
        RagChunkDO updated = chunkMapper.selectByDocId("d1").get(0);
        assertThat(updated.getIndexStatus()).isEqualTo("READY");
        assertThat(updated.getEmbeddingModel()).isEqualTo("model-a");
        assertThat(updated.getEmbeddingDimension()).isEqualTo(384);

        assertThat(chunkMapper.deleteByDocId("d1")).isEqualTo(2);
        assertThat(chunkMapper.countByDocId("d1")).isZero();
    }

    private RagDocumentDO document(String docId, String projectId, String hash, byte[] raw) {
        Instant now = Instant.now();
        RagDocumentDO document = new RagDocumentDO();
        document.setDocId(docId);
        document.setProjectId(projectId);
        document.setSource("a.txt");
        document.setContentType("text/plain");
        document.setSizeBytes(raw.length);
        document.setContentHash(hash);
        document.setRawContent(raw);
        document.setChunkCount(0);
        document.setIndexStatus("PENDING");
        document.setEmbeddingCost(BigDecimal.ZERO);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return document;
    }

    private RagChunkDO chunk(String chunkId, String docId, int index, String content) {
        RagChunkDO chunk = new RagChunkDO();
        chunk.setChunkId(chunkId);
        chunk.setDocId(docId);
        chunk.setProjectId("p1");
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setSource("a.txt");
        chunk.setIndexStatus("PENDING");
        chunk.setCreatedAt(Instant.now());
        return chunk;
    }
}
