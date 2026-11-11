package com.qtua.aihub.common.rag;

import java.util.List;

/**
 * 向量存储端口。
 *
 * <p>默认实现为 PostgreSQL pgvector；测试/无 PG 场景使用内存实现。所有方法按
 * `chunk_id` 幂等：同一 chunk 重复写入覆盖旧向量。检索必须按 project_id 与模型版本隔离。</p>
 */
public interface VectorStore {

    /**
     * 批量写入或覆盖向量。
     *
     * @param records 向量记录
     */
    void upsertAll(List<VectorRecord> records);

    /**
     * 删除某个文档的全部向量。
     *
     * @param documentId 文档 ID
     * @return 删除条数
     */
    int deleteByDocument(String documentId);

    /**
     * 相似度检索（cosine）。
     *
     * @param query 检索条件
     * @return 命中列表（score 降序）；无命中返回空列表
     */
    List<VectorMatch> search(VectorSearchQuery query);

    /**
     * 统计某个文档的向量条数。
     *
     * @param documentId 文档 ID
     * @return 条数
     */
    int countByDocument(String documentId);
}
