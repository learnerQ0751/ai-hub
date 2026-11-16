package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.RagDocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * `rag_documents` 表 Mapper。
 *
 * <p>列表查询不返回原始文件字节；按 `project_id + content_hash` 做去重查询。
 * 状态与索引元数据分开更新，避免覆盖并发写入的字段。</p>
 */
@Mapper
public interface RagDocumentMapper {

    /**
     * 插入文档（含原始文件）。
     *
     * @param document 数据对象
     */
    void insert(RagDocumentDO document);

    /**
     * 按 doc_id 查询（含原始文件，仅内部使用）。
     *
     * @param docId 文档 ID
     * @return 文档；不存在为 null
     */
    RagDocumentDO selectByDocId(@Param("docId") String docId);

    /**
     * 按 doc_id + 租户查询（租户隔离）。
     *
     * @param docId    文档 ID
     * @param tenantId 租户
     * @return 文档；不存在为 null
     */
    RagDocumentDO selectByDocIdInTenant(@Param("docId") String docId,
                                        @Param("tenantId") String tenantId);

    /**
     * 按项目 + hash 查询，用于去重。
     *
     * @param projectId   项目 ID
     * @param contentHash 文件 SHA-256
     * @return 文档；不存在为 null
     */
    RagDocumentDO selectByProjectAndHash(@Param("projectId") String projectId,
                                         @Param("contentHash") String contentHash);

    /**
     * 按租户 + 项目 + hash 查询，用于去重。
     *
     * @param tenantId    租户
     * @param projectId   项目 ID
     * @param contentHash 文件 SHA-256
     * @return 文档；不存在为 null
     */
    RagDocumentDO selectByProjectAndHashInTenant(@Param("tenantId") String tenantId,
                                                 @Param("projectId") String projectId,
                                                 @Param("contentHash") String contentHash);

    /**
     * 按项目查询文档列表（按创建时间倒序）。
     *
     * @param projectId 项目 ID
     * @param limit     返回条数
     * @return 文档列表（不含原始文件）
     */
    List<RagDocumentDO> selectByProject(@Param("projectId") String projectId,
                                        @Param("limit") int limit);

    /**
     * 按租户 + 项目查询文档列表。
     *
     * @param tenantId  租户
     * @param projectId 项目 ID
     * @param limit     返回条数
     * @return 文档列表
     */
    List<RagDocumentDO> selectByProjectInTenant(@Param("tenantId") String tenantId,
                                                @Param("projectId") String projectId,
                                                @Param("limit") int limit);

    /**
     * 更新索引状态与错误信息；errorMessage 为 null 时清空。
     *
     * @param docId        文档 ID
     * @param indexStatus  状态
     * @param errorMessage 错误信息
     * @param updatedAt    更新时间
     * @return 更新条数
     */
    int updateStatus(@Param("docId") String docId,
                     @Param("indexStatus") String indexStatus,
                     @Param("errorMessage") String errorMessage,
                     @Param("updatedAt") Instant updatedAt);

    /**
     * 更新切片数。
     *
     * @param docId      文档 ID
     * @param chunkCount 切片数
     * @param updatedAt  更新时间
     * @return 更新条数
     */
    int updateChunkCount(@Param("docId") String docId,
                         @Param("chunkCount") int chunkCount,
                         @Param("updatedAt") Instant updatedAt);

    /**
     * 更新 embedding 元数据与耗时/成本。
     *
     * @param docId               文档 ID
     * @param embeddingModel      模型标识
     * @param embeddingDimension  维度
     * @param embeddingDurationMs 耗时（毫秒）
     * @param embeddingCost       成本
     * @param updatedAt           更新时间
     * @return 更新条数
     */
    int updateEmbeddingMeta(@Param("docId") String docId,
                            @Param("embeddingModel") String embeddingModel,
                            @Param("embeddingDimension") Integer embeddingDimension,
                            @Param("embeddingDurationMs") Long embeddingDurationMs,
                            @Param("embeddingCost") BigDecimal embeddingCost,
                            @Param("updatedAt") Instant updatedAt);

    /**
     * 按 doc_id 删除文档。
     *
     * @param docId 文档 ID
     * @return 删除条数
     */
    int deleteByDocId(@Param("docId") String docId);
}
