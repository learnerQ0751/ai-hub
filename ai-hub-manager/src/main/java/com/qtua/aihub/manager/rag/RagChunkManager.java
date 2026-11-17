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
     * 按文档游标分页查询切片（REQ-3.10：索引逐批加载）。
     *
     * @param docId           文档 ID
     * @param afterChunkIndex 游标；首页传 -1
     * @param limit           每页条数
     * @return 切片列表（按 chunk_index 升序）
     */
    public List<RagChunkBO> listPageByDocId(String docId, int afterChunkIndex, int limit) {
        int pageSize = Math.max(1, limit);
        return chunkMapper.selectPageByDocId(docId, afterChunkIndex, pageSize).stream()
                .map(this::toBO)
                .toList();
    }

    /**
     * 按租户/项目/文档偏移分页查询切片（§7.1 文档切片列表）。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param docId     文档 ID
     * @param limit     每页条数
     * @param offset    偏移
     * @return 切片列表（按 chunk_index 升序）
     */
    public List<RagChunkBO> listPageByDocIdInTenant(String tenantId, String projectId,
                                                    String docId, int limit, int offset) {
        return chunkMapper.selectPageByDocIdInTenant(tenantId, projectId, docId,
                        Math.max(1, limit), Math.max(0, offset)).stream()
                .map(this::toBO).toList();
    }

    /**
     * 按切片 ID 批量查询（检索补齐正文）。
     *
     * @param chunkIds 切片 ID 列表
     * @return 切片列表；空入参返回空列表
     */
    public List<RagChunkBO> listByChunkIds(List<String> chunkIds) {
        if (ObjectUtils.isEmpty(chunkIds)) {
            return List.of();
        }
        return chunkMapper.selectByChunkIds(chunkIds).stream().map(this::toBO).toList();
    }

    /**
     * 按切片 ID 批量查询（租户隔离）。
     *
     * @param tenantId 租户
     * @param chunkIds 切片 ID 列表
     * @return 切片列表；空入参返回空列表
     */
    public List<RagChunkBO> listByChunkIdsInTenant(String tenantId, List<String> chunkIds) {
        if (ObjectUtils.isEmpty(chunkIds)) {
            return List.of();
        }
        return chunkMapper.selectByChunkIdsInTenant(tenantId, chunkIds).stream()
                .map(this::toBO).toList();
    }

    /**
     * 按文档与序号区间查询切片（REQ-5.4 邻接扩展，租户/项目下推）。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param docId     文档 ID
     * @param fromIndex 起始序号（含）
     * @param toIndex   结束序号（含）
     * @return 切片列表；区间非法或空文档返回空列表
     */
    public List<RagChunkBO> listByDocIdIndexRange(String tenantId, String projectId,
                                                  String docId, int fromIndex, int toIndex) {
        if (toIndex < fromIndex) {
            return List.of();
        }
        return chunkMapper.selectByDocIdIndexRange(tenantId, projectId, docId,
                Math.max(0, fromIndex), toIndex).stream().map(this::toBO).toList();
    }

    /**
     * 字面召回（REQ-5.3）。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param terms     查询词项
     * @param limit     召回上限
     * @return 切片列表（按命中词项数降序）
     */
    public List<RagChunkBO> searchKeyword(String tenantId, String projectId, List<String> terms,
                                          int limit) {
        if (ObjectUtils.isEmpty(terms)) {
            return List.of();
        }
        return chunkMapper.searchKeyword(tenantId, projectId, terms, Math.max(1, limit)).stream()
                .map(this::toBO).toList();
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
        dataObject.setTenantId(chunk.tenantId());
        return dataObject;
    }

    private RagChunkBO toBO(RagChunkDO dataObject) {
        return new RagChunkBO(dataObject.getChunkId(), dataObject.getDocId(),
                dataObject.getProjectId(), dataObject.getChunkIndex(), dataObject.getContent(),
                dataObject.getCharCount(), dataObject.getSource(), dataObject.getIndexStatus(),
                dataObject.getEmbeddingModel(), dataObject.getEmbeddingDimension(),
                dataObject.getErrorMessage(), dataObject.getCreatedAt(),
                dataObject.getTenantId());
    }
}
