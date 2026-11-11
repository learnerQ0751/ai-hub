package com.qtua.aihub.manager.rag;

import com.qtua.aihub.common.rag.VectorMatch;
import com.qtua.aihub.common.rag.VectorRecord;
import com.qtua.aihub.common.rag.VectorSearchQuery;
import com.qtua.aihub.common.rag.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储（测试 / 无 PostgreSQL 场景）。
 *
 * <p>按 `chunk_id` 幂等覆盖；检索按 project_id 与模型版本过滤，cosine 相似度降序。
 * 使用并发 Map，但仅作为单实例存储，不承担跨实例一致性；生产默认 pgvector。</p>
 */
@Component
@ConditionalOnProperty(prefix = "gateway.rag.vector-store", name = "type",
        havingValue = "memory")
public class InMemoryVectorStore implements VectorStore {

    private static final double ZERO_SCORE = 0.0d;

    private final Map<String, VectorRecord> records = new ConcurrentHashMap<>();

    /**
     * 批量写入或覆盖向量。
     *
     * @param vectorRecords 向量记录
     */
    @Override
    public void upsertAll(List<VectorRecord> vectorRecords) {
        if (Objects.isNull(vectorRecords) || vectorRecords.isEmpty()) {
            return;
        }
        for (VectorRecord record : vectorRecords) {
            records.put(record.chunkId(), record);
        }
    }

    /**
     * 删除文档全部向量。
     *
     * @param documentId 文档 ID
     * @return 删除条数
     */
    @Override
    public int deleteByDocument(String documentId) {
        int before = records.size();
        records.entrySet().removeIf(entry -> Objects.equals(entry.getValue().documentId(),
                documentId));
        return before - records.size();
    }

    /**
     * 相似度检索。
     *
     * @param query 检索条件
     * @return 命中列表（分数降序）
     */
    @Override
    public List<VectorMatch> search(VectorSearchQuery query) {
        float[] queryVector = query.vector();
        int topK = Math.max(1, query.topK());
        return records.values().stream()
                .filter(record -> Objects.equals(record.projectId(), query.projectId()))
                .filter(record -> Objects.equals(record.modelId(), query.modelId()))
                .filter(record -> record.dimension() == queryVector.length)
                .map(record -> new VectorMatch(record.chunkId(), record.documentId(),
                        record.projectId(), record.modelId(),
                        cosine(queryVector, record.vector())))
                .filter(match -> match.score() >= query.minScore())
                .sorted(Comparator.comparingDouble(VectorMatch::score).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 统计文档向量数。
     *
     * @param documentId 文档 ID
     * @return 条数
     */
    @Override
    public int countByDocument(String documentId) {
        return (int) records.values().stream()
                .filter(record -> Objects.equals(record.documentId(), documentId))
                .count();
    }

    /**
     * cosine 相似度；零向量返回 0。
     *
     * @param left  向量 A
     * @param right 向量 B
     * @return 相似度
     */
    private double cosine(float[] left, float[] right) {
        double dot = ZERO_SCORE;
        double leftNorm = ZERO_SCORE;
        double rightNorm = ZERO_SCORE;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm <= ZERO_SCORE || rightNorm <= ZERO_SCORE) {
            return ZERO_SCORE;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
