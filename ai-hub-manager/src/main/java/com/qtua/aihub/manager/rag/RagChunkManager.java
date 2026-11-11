package com.qtua.aihub.manager.rag;

import com.qtua.aihub.dal.mapper.RagChunkMapper;
import com.qtua.aihub.dal.model.RagChunkDO;
import com.qtua.aihub.manager.model.RagChunkBO;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * RAG 切片数据门面。
 *
 * <p>批量插入按调用方给定批大小拆分，避免单条 SQL 过长；状态按文档整体推进。
 * 本类无可变业务状态，多实例安全。</p>
 */
@Component
public class RagChunkManager {

    private final RagChunkMapper chunkMapper;

    /**
     * 构造门面。
     *
     * @param chunkMapper 切片 Mapper
     */
    public RagChunkManager(RagChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    /**
     * 分批插入切片。
     *
     * @param chunks    切片列表
     * @param batchSize 每批条数
     */
    public void insertAll(List<RagChunkBO> chunks, int batchSize) {
        if (ObjectUtils.isEmpty(chunks)) {
            return;
        }
        int size = Math.max(1, batchSize);
        for (int start = 0; start < chunks.size(); start += size) {
            int end = Math.min(start + size, chunks.size());
            chunkMapper.insertBatch(chunks.subList(start, end).stream().map(this::toDO).toList());
        }
    }

    /**
     * 按文档查询切片。
     *
     * @param docId 文档 ID
     * @return 切片列表
     */
    public List<RagChunkBO> listByDocId(String docId) {
        return chunkMapper.selectByDocId(docId).stream().map(this::toBO).toList();
    }

    /**
     * 统计文档切片数。
     *
     * @param docId 文档 ID
     * @return 切片数
     */
    public int countByDocId(String docId) {
        return chunkMapper.countByDocId(docId);
    }

    /**
     * 更新文档全部切片状态。
     *
     * @param docId        文档 ID
     * @param status       状态
     * @param errorMessage 错误信息；null 清空
     */
    public void updateStatusByDocId(String docId, String status, String errorMessage) {
        chunkMapper.updateStatusByDocId(docId, status, errorMessage);
    }

    /**
     * 更新文档全部切片的模型与维度。
     *
     * @param docId     文档 ID
     * @param modelId   模型标识
     * @param dimension 维度
     */
    public void updateEmbeddingMetaByDocId(String docId, String modelId, int dimension) {
        chunkMapper.updateEmbeddingMetaByDocId(docId, modelId, dimension);
    }

    /**
     * 按文档删除切片。
     *
     * @param docId 文档 ID
     * @return 删除条数
     */
    public int deleteByDocId(String docId) {
        return chunkMapper.deleteByDocId(docId);
    }

    private RagChunkDO toDO(RagChunkBO chunk) {
        RagChunkDO dataObject = new RagChunkDO();
        dataObject.setChunkId(chunk.chunkId());
        dataObject.setDocId(chunk.docId());
        dataObject.setProjectId(chunk.projectId());
        dataObject.setChunkIndex(chunk.chunkIndex());
        dataObject.setContent(chunk.content());
        dataObject.setCharCount(chunk.charCount());
        dataObject.setSource(chunk.source());
        dataObject.setIndexStatus(chunk.indexStatus());
        dataObject.setEmbeddingModel(chunk.embeddingModel());
        dataObject.setEmbeddingDimension(chunk.embeddingDimension());
        dataObject.setErrorMessage(chunk.errorMessage());
        dataObject.setCreatedAt(chunk.createdAt());
        return dataObject;
    }

    private RagChunkBO toBO(RagChunkDO dataObject) {
        return new RagChunkBO(dataObject.getChunkId(), dataObject.getDocId(),
                dataObject.getProjectId(), dataObject.getChunkIndex(), dataObject.getContent(),
                dataObject.getCharCount(), dataObject.getSource(), dataObject.getIndexStatus(),
                dataObject.getEmbeddingModel(), dataObject.getEmbeddingDimension(),
                dataObject.getErrorMessage(), dataObject.getCreatedAt());
    }
}
