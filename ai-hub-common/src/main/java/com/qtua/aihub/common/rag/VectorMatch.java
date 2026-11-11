package com.qtua.aihub.common.rag;

/**
 * 一次向量检索命中。
 *
 * @param chunkId    切片 ID
 * @param documentId 文档 ID
 * @param projectId  项目 ID
 * @param modelId    模型标识
 * @param score      相似度（cosine，越大越相似）
 */
public record VectorMatch(String chunkId, String documentId, String projectId,
                          String modelId, double score) {
}
