package com.qtua.aihub.manager.model;

import java.time.Instant;

/**
 * RAG 切片业务对象。
 *
 * @param chunkId            切片 ID
 * @param docId              文档 ID
 * @param projectId          项目 ID
 * @param chunkIndex         切片序号
 * @param content            切片文本
 * @param charCount          字符数
 * @param source             原始文件名
 * @param indexStatus        索引状态
 * @param embeddingModel     向量模型标识
 * @param embeddingDimension 向量维度
 * @param errorMessage       错误信息
 * @param createdAt          创建时间
 */
public record RagChunkBO(String chunkId, String docId, String projectId, int chunkIndex,
                         String content, int charCount, String source, String indexStatus,
                         String embeddingModel, Integer embeddingDimension, String errorMessage,
                         Instant createdAt) {
}
