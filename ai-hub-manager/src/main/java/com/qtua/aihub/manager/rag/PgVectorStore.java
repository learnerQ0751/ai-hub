package com.qtua.aihub.manager.rag;

import com.qtua.aihub.common.rag.VectorMatch;
import com.qtua.aihub.common.rag.VectorRecord;
import com.qtua.aihub.common.rag.VectorSearchQuery;
import com.qtua.aihub.common.rag.VectorStore;
import com.qtua.aihub.dal.mapper.RagVectorMapper;
import com.qtua.aihub.dal.model.RagVectorDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL pgvector 向量存储（生产默认）。
 *
 * <p>向量列固定 {@code vector(1024)}：低维模型（384/512/768）零填充到 1024 后写入，
 * cosine 距离与零填充无关，因此单列可承载多种模型；维度大于 1024 的模型不支持，需迁移列。
 * 检索使用 cosine 距离（1 - distance 作为 score），过滤条件下推到 SQL，HNSW 索引用于排序。
 * 本类无可变业务状态，多实例安全。</p>
 */
@Component
@ConditionalOnProperty(prefix = "gateway.rag.vector-store", name = "type",
        havingValue = "pgvector", matchIfMissing = true)
public class PgVectorStore implements VectorStore {

    /** 与 V9 迁移后的向量列维度一致。 */
    private static final int VECTOR_COLUMN_DIMENSION = 1024;

    private final RagVectorMapper vectorMapper;

    /**
     * 构造存储。
     *
     * @param vectorMapper 向量 Mapper
     */
    public PgVectorStore(RagVectorMapper vectorMapper) {
        this.vectorMapper = vectorMapper;
    }

    /**
     * 批量写入或覆盖向量（ON CONFLICT chunk_id 幂等）。
     *
     * @param vectorRecords 向量记录
     */
    @Override
    public void upsertAll(List<VectorRecord> vectorRecords) {
        if (ObjectUtils.isEmpty(vectorRecords)) {
            return;
        }
        vectorMapper.insertBatch(vectorRecords.stream().map(this::toDO).toList());
    }

    /**
     * 删除文档全部向量。
     *
     * @param documentId 文档 ID
     * @return 删除条数
     */
    @Override
    public int deleteByDocument(String documentId) {
        return vectorMapper.deleteByDocId(documentId);
    }

    /**
     * 相似度检索。
     *
     * @param query 检索条件
     * @return 命中列表
     */
    @Override
    public List<VectorMatch> search(VectorSearchQuery query) {
        int topK = Math.max(1, query.topK());
        String vectorText = toVectorText(query.vector());
        return vectorMapper.selectSimilar(query.projectId(), query.modelId(), vectorText, topK,
                        query.minScore())
                .stream()
                .map(this::toMatch)
                .filter(match -> Objects.nonNull(match))
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
        return vectorMapper.countByDocId(documentId);
    }

    private RagVectorDO toDO(VectorRecord record) {
        RagVectorDO dataObject = new RagVectorDO();
        dataObject.setChunkId(record.chunkId());
        dataObject.setDocId(record.documentId());
        dataObject.setProjectId(record.projectId());
        dataObject.setEmbeddingModel(record.modelId());
        dataObject.setEmbeddingDimension(record.dimension());
        dataObject.setEmbedding(toVectorText(record.vector()));
        dataObject.setCreatedAt(record.createdAt());
        return dataObject;
    }

    private VectorMatch toMatch(RagVectorDO dataObject) {
        if (Objects.isNull(dataObject.getScore())) {
            return null;
        }
        return new VectorMatch(dataObject.getChunkId(), dataObject.getDocId(),
                dataObject.getProjectId(), dataObject.getEmbeddingModel(), dataObject.getScore());
    }

    /**
     * 转为 pgvector 文本；使用 Float.toString 保留精度，低维向量零填充到列维度。
     *
     * @param vector 向量
     * @return "[v1,v2,...]"
     */
    private String toVectorText(float[] vector) {
        if (vector.length > VECTOR_COLUMN_DIMENSION) {
            throw new IllegalStateException("embedding dimension " + vector.length
                    + " exceeds vector column dimension " + VECTOR_COLUMN_DIMENSION);
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < VECTOR_COLUMN_DIMENSION; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(index < vector.length ? vector[index] : 0.0f));
        }
        return builder.append(']').toString();
    }
}
