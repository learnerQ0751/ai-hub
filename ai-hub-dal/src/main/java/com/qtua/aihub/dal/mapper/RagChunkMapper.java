package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.RagChunkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * `rag_chunks` 表 Mapper。
 *
 * <p>切片按 doc_id 批量插入、按 doc_id 查询/删除；索引状态按文档整体推进。</p>
 */
@Mapper
public interface RagChunkMapper {

    /**
     * 批量插入切片。
     *
     * @param chunks 切片列表
     */
    void insertBatch(@Param("chunks") List<RagChunkDO> chunks);

    /**
     * 按文档查询切片（按序号升序）。
     *
     * @param docId 文档 ID
     * @return 切片列表
     */
    List<RagChunkDO> selectByDocId(@Param("docId") String docId);

    /**
     * 按文档游标分页查询切片（索引逐批加载）。
     *
     * @param docId           文档 ID
     * @param afterChunkIndex 游标：只返回 chunk_index 大于该值的切片；首页传 -1
     * @param limit           每页条数
     * @return 切片列表（按 chunk_index 升序）
     */
    List<RagChunkDO> selectPageByDocId(@Param("docId") String docId,
                                       @Param("afterChunkIndex") int afterChunkIndex,
                                       @Param("limit") int limit);

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
    List<RagChunkDO> selectPageByDocIdInTenant(@Param("tenantId") String tenantId,
                                               @Param("projectId") String projectId,
                                               @Param("docId") String docId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    /**
     * 按切片 ID 批量查询（检索补齐正文）；调用方负责控制每批数量。
     *
     * @param chunkIds 切片 ID 列表（非空）
     * @return 切片列表
     */
    List<RagChunkDO> selectByChunkIds(@Param("chunkIds") List<String> chunkIds);

    /**
     * 按切片 ID 批量查询（租户隔离）。
     *
     * @param tenantId 租户
     * @param chunkIds 切片 ID 列表
     * @return 切片列表
     */
    List<RagChunkDO> selectByChunkIdsInTenant(@Param("tenantId") String tenantId,
                                              @Param("chunkIds") List<String> chunkIds);

    /**
     * 字面召回（REQ-5.3）：按租户/项目与词项做大小写不敏感包含匹配。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param terms     查询词项（非空）
     * @param limit     召回上限
     * @return 切片列表（按命中词项数降序）
     */
    List<RagChunkDO> searchKeyword(@Param("tenantId") String tenantId,
                                   @Param("projectId") String projectId,
                                   @Param("terms") List<String> terms,
                                   @Param("limit") int limit);

    /**
     * 按文档与序号区间查询切片（REQ-5.4 邻接扩展，租户/项目下推）。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param docId     文档 ID
     * @param fromIndex 起始序号（含）
     * @param toIndex   结束序号（含）
     * @return 切片列表（按 chunk_index 升序）
     */
    List<RagChunkDO> selectByDocIdIndexRange(@Param("tenantId") String tenantId,
                                             @Param("projectId") String projectId,
                                             @Param("docId") String docId,
                                             @Param("fromIndex") int fromIndex,
                                             @Param("toIndex") int toIndex);

    /**
     * 统计文档切片数。
     *
     * @param docId 文档 ID
     * @return 切片数
     */
    int countByDocId(@Param("docId") String docId);

    /**
     * 更新文档全部切片的索引状态与错误信息。
     *
     * @param docId        文档 ID
     * @param indexStatus  状态
     * @param errorMessage 错误信息；null 清空
     * @return 更新条数
     */
    int updateStatusByDocId(@Param("docId") String docId,
                            @Param("indexStatus") String indexStatus,
                            @Param("errorMessage") String errorMessage);

    /**
     * 更新文档全部切片的 embedding 模型与维度。
     *
     * @param docId              文档 ID
     * @param embeddingModel     模型标识
     * @param embeddingDimension 维度
     * @return 更新条数
     */
    int updateEmbeddingMetaByDocId(@Param("docId") String docId,
                                   @Param("embeddingModel") String embeddingModel,
                                   @Param("embeddingDimension") Integer embeddingDimension);

    /**
     * 按文档删除切片。
     *
     * @param docId 文档 ID
     * @return 删除条数
     */
    int deleteByDocId(@Param("docId") String docId);
}
