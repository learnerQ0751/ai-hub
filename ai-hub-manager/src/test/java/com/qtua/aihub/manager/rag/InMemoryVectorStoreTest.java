package com.qtua.aihub.manager.rag;

import com.qtua.aihub.common.rag.VectorMatch;
import com.qtua.aihub.common.rag.VectorRecord;
import com.qtua.aihub.common.rag.VectorSearchQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.2 内存向量存储测试：写入/覆盖、cosine 检索、项目与模型隔离、删除。
 */
class InMemoryVectorStoreTest {

    private final InMemoryVectorStore store = new InMemoryVectorStore();

    @Test
    void upserts_and_searches_by_project_and_model() {
        store.upsertAll(List.of(record("c1", "d1", "p1", "m1", new float[]{1.0f, 0.0f}),
                record("c2", "d1", "p1", "m1", new float[]{0.0f, 1.0f}),
                record("c3", "d2", "p2", "m1", new float[]{1.0f, 0.0f}),
                record("c4", "d3", "p1", "m2", new float[]{1.0f, 0.0f})));

        List<VectorMatch> matches = store.search(new VectorSearchQuery("p1", "m1",
                new float[]{1.0f, 0.0f}, 10, 0.5));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).chunkId()).isEqualTo("c1");
        assertThat(matches.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void respects_top_k_and_min_score() {
        store.upsertAll(List.of(record("c1", "d1", "p1", "m1", new float[]{1.0f, 0.0f}),
                record("c2", "d1", "p1", "m1", new float[]{0.7f, 0.7f}),
                record("c3", "d1", "p1", "m1", new float[]{0.0f, 1.0f})));

        List<VectorMatch> topOne = store.search(new VectorSearchQuery("p1", "m1",
                new float[]{1.0f, 0.0f}, 1, 0.0));

        assertThat(topOne).hasSize(1);
        assertThat(topOne.get(0).chunkId()).isEqualTo("c1");
        assertThat(store.search(new VectorSearchQuery("p1", "m1",
                new float[]{1.0f, 0.0f}, 10, 0.99))).extracting(VectorMatch::chunkId)
                .containsExactly("c1");
    }

    @Test
    void upsert_overwrites_same_chunk() {
        store.upsertAll(List.of(record("c1", "d1", "p1", "m1", new float[]{1.0f, 0.0f})));
        store.upsertAll(List.of(record("c1", "d1", "p1", "m1", new float[]{0.0f, 1.0f})));

        List<VectorMatch> matches = store.search(new VectorSearchQuery("p1", "m1",
                new float[]{0.0f, 1.0f}, 10, 0.5));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void deletes_by_document_and_counts() {
        store.upsertAll(List.of(record("c1", "d1", "p1", "m1", new float[]{1.0f, 0.0f}),
                record("c2", "d1", "p1", "m1", new float[]{0.0f, 1.0f}),
                record("c3", "d2", "p1", "m1", new float[]{1.0f, 0.0f})));

        assertThat(store.countByDocument("d1")).isEqualTo(2);
        assertThat(store.deleteByDocument("d1")).isEqualTo(2);
        assertThat(store.countByDocument("d1")).isZero();
        assertThat(store.countByDocument("d2")).isEqualTo(1);
    }

    private VectorRecord record(String chunkId, String docId, String projectId, String model,
                                float[] vector) {
        return new VectorRecord(chunkId, docId, projectId, model, vector.length, vector,
                Instant.now());
    }
}
