package com.qtua.aihub;

import com.qtua.aihub.common.rag.VectorMatch;
import com.qtua.aihub.common.rag.VectorRecord;
import com.qtua.aihub.common.rag.VectorSearchQuery;
import com.qtua.aihub.manager.rag.PgVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.2 PgVectorStore 真库集成测试。
 *
 * <p>使用 Testcontainers 启动真实 pgvector（PostgreSQL 16）；无 Docker 时自动跳过。
 * 验证 Flyway 迁移（V6/V7/V8 在真实 PostgreSQL 上执行）、向量 upsert（幂等）、
 * 按 project/模型隔离的相似度检索、按文档级联删除。</p>
 *
 * <p>镜像默认取本机已有的 daocloud 镜像名，可用 {@code -Drag.pgvector.image} 覆盖。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AiHubApplication.class)
@ActiveProfiles("test")
class RagVectorStoreIntegrationTest {

    private static final String IMAGE = System.getProperty("rag.pgvector.image",
            "m.daocloud.io/docker.io/pgvector/pgvector:pg16");

    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(IMAGE)
            .withEnv("POSTGRES_DB", "aihub")
            .withEnv("POSTGRES_USER", "aihub")
            .withEnv("POSTGRES_PASSWORD", "aihub")
            .withExposedPorts(5432);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/aihub");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> "aihub");
        registry.add("spring.datasource.password", () -> "aihub");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("gateway.rag.vector-store.type", () -> "pgvector");
    }

    @Autowired
    private PgVectorStore vectorStore;

    @Test
    void upsert_search_and_delete_roundtrip() {
        float[] base = unitVector(0);
        float[] other = unitVector(1);
        VectorRecord recordA = record("chunk-a", "doc-1", "project-a", base);
        VectorRecord recordB = record("chunk-b", "doc-1", "project-a", other);
        VectorRecord recordOtherProject = record("chunk-c", "doc-2", "project-b", base);

        vectorStore.upsertAll(List.of(recordA, recordB, recordOtherProject));

        assertThat(vectorStore.countByDocument("doc-1")).isEqualTo(2);

        List<VectorMatch> hits = vectorStore.search(
                new VectorSearchQuery("project-a", "model-a", base, 5, 0.0));
        assertThat(hits).extracting(VectorMatch::chunkId)
                .containsExactly("chunk-a", "chunk-b");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());

        // chunk_id 幂等覆盖：重复写入不产生新行
        vectorStore.upsertAll(List.of(recordA));
        assertThat(vectorStore.countByDocument("doc-1")).isEqualTo(2);

        assertThat(vectorStore.deleteByDocument("doc-1")).isEqualTo(2);
        assertThat(vectorStore.countByDocument("doc-1")).isZero();
        assertThat(vectorStore.countByDocument("doc-2")).isEqualTo(1);
    }

    private static VectorRecord record(String chunkId, String documentId, String projectId,
                                       float[] vector) {
        return new VectorRecord(chunkId, documentId, projectId, "model-a", vector.length, vector,
                Instant.now());
    }

    private static float[] unitVector(int hotIndex) {
        float[] vector = new float[384];
        vector[hotIndex] = 1.0f;
        return vector;
    }
}
