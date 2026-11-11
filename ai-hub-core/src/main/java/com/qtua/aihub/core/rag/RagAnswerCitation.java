package com.qtua.aihub.core.rag;

/**
 * 实际进入 prompt 的一条引用。
 *
 * @param index   引用编号（从 1 开始，与 prompt 中的 `[n]` 对应）
 * @param docId   文档 ID
 * @param chunkId 切片 ID
 * @param source  来源文件名
 * @param score   最终排序分（重排开启时为重排分）
 */
public record RagAnswerCitation(int index, String docId, String chunkId, String source,
                                double score) {
}
