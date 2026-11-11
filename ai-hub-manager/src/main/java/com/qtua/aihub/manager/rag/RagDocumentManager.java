package com.qtua.aihub.manager.rag;

import com.qtua.aihub.dal.mapper.RagDocumentMapper;
import com.qtua.aihub.dal.model.RagDocumentDO;
import com.qtua.aihub.manager.model.RagDocumentBO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * RAG 文档数据门面。
 *
 * <p>BO↔DO 转换与 Mapper 封装；不做业务编排。所有写操作是单条 SQL，
 * 事务边界由 Service 在需要级联删除时提供。本类无可变业务状态，多实例安全。</p>
 */
@Component
public class RagDocumentManager {

    private final RagDocumentMapper documentMapper;

    /**
     * 构造门面。
     *
     * @param documentMapper 文档 Mapper
     */
    public RagDocumentManager(RagDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    /**
     * 插入文档。
     *
     * @param document 业务对象
     */
    public void insert(RagDocumentBO document) {
        documentMapper.insert(toDO(document));
    }

    /**
     * 按 doc_id 查询。
     *
     * @param docId 文档 ID
     * @return 文档；不存在为空
     */
    public Optional<RagDocumentBO> findByDocId(String docId) {
        return Optional.ofNullable(documentMapper.selectByDocId(docId)).map(this::toBO);
    }

    /**
     * 按项目 + hash 查询（去重）。
     *
     * @param projectId   项目 ID
     * @param contentHash 文件 SHA-256
     * @return 文档；不存在为空
     */
    public Optional<RagDocumentBO> findByProjectAndHash(String projectId, String contentHash) {
        return Optional.ofNullable(documentMapper.selectByProjectAndHash(projectId, contentHash))
                .map(this::toBO);
    }

    /**
     * 按项目查询文档列表。
     *
     * @param projectId 项目 ID
     * @param limit     返回条数
     * @return 文档列表
     */
    public List<RagDocumentBO> listByProject(String projectId, int limit) {
        return documentMapper.selectByProject(projectId, limit).stream().map(this::toBO).toList();
    }

    /**
     * 更新索引状态与错误信息。
     *
     * @param docId        文档 ID
     * @param status       状态
     * @param errorMessage 错误信息；null 清空
     */
    public void updateStatus(String docId, String status, String errorMessage) {
        documentMapper.updateStatus(docId, status, errorMessage, Instant.now());
    }

    /**
     * 更新切片数。
     *
     * @param docId      文档 ID
     * @param chunkCount 切片数
     */
    public void updateChunkCount(String docId, int chunkCount) {
        documentMapper.updateChunkCount(docId, chunkCount, Instant.now());
    }

    /**
     * 更新 embedding 元数据。
     *
     * @param docId              文档 ID
     * @param modelId            模型标识
     * @param dimension          维度
     * @param durationMs         耗时（毫秒）
     * @param cost               成本
     */
    public void updateEmbeddingMeta(String docId, String modelId, int dimension,
                                    long durationMs, BigDecimal cost) {
        documentMapper.updateEmbeddingMeta(docId, modelId, dimension, durationMs, cost,
                Instant.now());
    }

    /**
     * 按 doc_id 删除文档。
     *
     * @param docId 文档 ID
     * @return 删除条数
     */
    public int deleteByDocId(String docId) {
        return documentMapper.deleteByDocId(docId);
    }

    private RagDocumentDO toDO(RagDocumentBO document) {
        RagDocumentDO dataObject = new RagDocumentDO();
        dataObject.setDocId(document.docId());
        dataObject.setProjectId(document.projectId());
        dataObject.setSource(document.source());
        dataObject.setContentType(document.contentType());
        dataObject.setSizeBytes(document.sizeBytes());
        dataObject.setContentHash(document.contentHash());
        dataObject.setRawContent(document.rawContent());
        dataObject.setChunkCount(document.chunkCount());
        dataObject.setIndexStatus(document.indexStatus());
        dataObject.setEmbeddingModel(document.embeddingModel());
        dataObject.setEmbeddingDimension(document.embeddingDimension());
        dataObject.setEmbeddingDurationMs(document.embeddingDurationMs());
        dataObject.setEmbeddingCost(document.embeddingCost());
        dataObject.setErrorMessage(document.errorMessage());
        dataObject.setCreatedAt(document.createdAt());
        dataObject.setUpdatedAt(document.updatedAt());
        return dataObject;
    }

    private RagDocumentBO toBO(RagDocumentDO dataObject) {
        return new RagDocumentBO(dataObject.getDocId(), dataObject.getProjectId(),
                dataObject.getSource(), dataObject.getContentType(), dataObject.getSizeBytes(),
                dataObject.getContentHash(), dataObject.getRawContent(), dataObject.getChunkCount(),
                dataObject.getIndexStatus(), dataObject.getEmbeddingModel(),
                dataObject.getEmbeddingDimension(), dataObject.getEmbeddingDurationMs(),
                dataObject.getEmbeddingCost(), dataObject.getErrorMessage(),
                dataObject.getCreatedAt(), dataObject.getUpdatedAt());
    }
}
