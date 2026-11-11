package com.qtua.aihub.core.rag;

/**
 * 文档删除结果。
 *
 * @param deleted       是否删除了文档
 * @param chunksDeleted 删除的切片数
 * @param vectorsDeleted 删除的向量数
 */
public record RagDeleteResult(boolean deleted, int chunksDeleted, int vectorsDeleted) {
}
