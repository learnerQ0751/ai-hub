package com.qtua.aihub.common.rag;

/**
 * 切分后的切片草稿（尚未落库、尚未向量化）。
 *
 * @param chunkIndex 切片序号（从 0 开始）
 * @param content    切片文本
 */
public record RagChunkDraft(int chunkIndex, String content) {

    /**
     * 字符数。
     *
     * @return 字符数
     */
    public int charCount() {
        return content.length();
    }
}
