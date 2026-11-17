package com.qtua.aihub.core.rag;

import com.qtua.aihub.common.rag.RagIndexStatus;
import com.qtua.aihub.manager.model.RagChunkBO;
import com.qtua.aihub.manager.rag.RagChunkManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-5.4 上下文拼装测试：文档内顺序还原、邻居扩展去重、租户/项目过滤与稳定性。
 */
class RagContextAssemblerTest {

    private static final String TENANT = "t1";
    private static final String PROJECT = "p1";

    private final RagChunkManager chunkManager = mock(RagChunkManager.class);
    private final RagContextAssembler assembler = new RagContextAssembler(chunkManager);

    @Test
    void restores_document_order_and_groups_without_expansion() {
        List<RagContextSegment> segments = assembler.assemble(List.of(
                hit("d1-2", "d1", 2, "丙"),
                hit("d2-0", "d2", 0, "戊"),
                hit("d1-0", "d1", 0, "甲")), TENANT, PROJECT, 0);

        assertThat(segments).extracting(RagContextSegment::chunkId)
                .containsExactly("d1-0", "d1-2", "d2-0");
        assertThat(segments).allSatisfy(segment -> assertThat(segment.citation()).isTrue());
    }

    @Test
    void expands_neighbors_and_deduplicates_overlapping_windows() {
        when(chunkManager.listByDocIdIndexRange(TENANT, PROJECT, "d1", 0, 3))
                .thenReturn(List.of(chunk("d1-0", "d1", 0, "邻居甲"),
                        chunk("d1-1", "d1", 1, "命中乙"),
                        chunk("d1-2", "d1", 2, "命中丙"),
                        chunk("d1-3", "d1", 3, "邻居丁")));

        List<RagContextSegment> segments = assembler.assemble(List.of(
                hit("d1-2", "d1", 2, "命中丙"),
                hit("d1-1", "d1", 1, "命中乙")), TENANT, PROJECT, 1);

        assertThat(segments).extracting(RagContextSegment::chunkId)
                .containsExactly("d1-0", "d1-1", "d1-2", "d1-3");
        assertThat(segments).extracting(RagContextSegment::citation)
                .containsExactly(false, true, true, false);
    }

    @Test
    void drops_neighbors_from_other_tenant_or_project() {
        when(chunkManager.listByDocIdIndexRange(TENANT, PROJECT, "d1", 0, 2))
                .thenReturn(List.of(chunk("d1-0", "d1", 0, "邻居甲"),
                        chunkWith("d1-2", "d1", 2, "越权丙", "t2", PROJECT),
                        chunkWith("d1-3", "d1", 3, "越权丁", TENANT, "p2")));

        List<RagContextSegment> segments = assembler.assemble(
                List.of(hit("d1-1", "d1", 1, "命中乙")), TENANT, PROJECT, 1);

        assertThat(segments).extracting(RagContextSegment::chunkId)
                .containsExactly("d1-0", "d1-1");
    }

    @Test
    void neighbor_query_failure_falls_back_to_anchors() {
        when(chunkManager.listByDocIdIndexRange(eq(TENANT), eq(PROJECT), eq("d1"), anyInt(),
                anyInt())).thenThrow(new IllegalStateException("db down"));

        List<RagContextSegment> segments = assembler.assemble(
                List.of(hit("d1-1", "d1", 1, "命中乙")), TENANT, PROJECT, 1);

        assertThat(segments).extracting(RagContextSegment::chunkId).containsExactly("d1-1");
        assertThat(segments.get(0).citation()).isTrue();
    }

    @Test
    void same_input_produces_identical_segments() {
        List<RagRetrievedChunk> hits = List.of(
                hit("d1-2", "d1", 2, "丙"),
                hit("d1-0", "d1", 0, "甲"));

        List<RagContextSegment> first = assembler.assemble(hits, TENANT, PROJECT, 0);
        List<RagContextSegment> second = assembler.assemble(hits, TENANT, PROJECT, 0);

        assertThat(second).isEqualTo(first);
    }

    private RagRetrievedChunk hit(String chunkId, String docId, int chunkIndex, String content) {
        return new RagRetrievedChunk(chunkId, docId, PROJECT, "a.md", chunkIndex, content, 0.8d,
                null);
    }

    private RagChunkBO chunk(String chunkId, String docId, int chunkIndex, String content) {
        return chunkWith(chunkId, docId, chunkIndex, content, TENANT, PROJECT);
    }

    private RagChunkBO chunkWith(String chunkId, String docId, int chunkIndex, String content,
                                 String tenantId, String projectId) {
        return new RagChunkBO(chunkId, docId, projectId, chunkIndex, content, content.length(),
                "a.md", RagIndexStatus.READY.name(), null, null, null, Instant.EPOCH, tenantId);
    }
}
