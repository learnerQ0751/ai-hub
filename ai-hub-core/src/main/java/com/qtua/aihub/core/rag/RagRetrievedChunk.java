package com.qtua.aihub.core.rag;

/**
 * 一次检索命中的切片。
 *
 * <p>`score` 是向量相似度（cosine）；开启规则重排时 `rerankScore` 为最终排序分，
 * 未重排为 null。Core 不依赖 api DTO，由 Service 转换为响应。</p>
 *
 * @param chunkId     切片 ID
 * @param docId       文档 ID
 * @param projectId   项目 ID
 * @param source      来源文件名
 * @param chunkIndex  切片序号
 * @param content     切片正文
 * @param score       向量相似度
 * @param rerankScore 规则重排分数；未重排为 null
 */
public record RagRetrievedChunk(String chunkId, String docId, String projectId, String source,
                                int chunkIndex, String content, double score,
                                Double rerankScore) {
}
