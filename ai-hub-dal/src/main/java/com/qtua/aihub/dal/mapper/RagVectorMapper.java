package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.RagVectorDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * `rag_vectors` 表 Mapper（仅 PostgreSQL pgvector）。
 *
 * <p>向量以文本形式绑定后 CAST 为 vector；检索用 cosine 距离并按下推的
 * project_id / embedding_model 过滤。H2 测试使用内存 VectorStore，不执行本 Mapper。</p>
 */
@Mapper
public interface RagVectorMapper {

    /**
     * 批量插入向量。
     *
     * @param vectors 向量列表
     */
    void insertBatch(@Param("vectors") List<RagVectorDO> vectors);

    /**
     * 按文档删除向量。
     *
     * @param docId 文档 ID
     * @return 删除条数
     */
    int deleteByDocId(@Param("docId") String docId);

    /**
     * 统计文档向量数。
     *
     * @param docId 文档 ID
     * @return 条数
     */
    int countByDocId(@Param("docId") String docId);

    /**
     * 相似度检索；分数 = 1 - cosine 距离。
     *
     * @param projectId 项目 ID
     * @param modelId   模型标识
     * @param vector    查询向量文本（pgvector 格式）
     * @param topK      返回条数
     * @param minScore  最小相似度
     * @return 命中列表（分数降序）
     */
    List<RagVectorDO> selectSimilar(@Param("projectId") String projectId,
                                    @Param("modelId") String modelId,
                                    @Param("vector") String vector,
                                    @Param("topK") int topK,
                                    @Param("minScore") double minScore);
}
