package com.qtua.aihub.core.rag;

import java.util.Objects;

/**
 * 回答侧上下文拼装片段（REQ-5.4）。
 *
 * <p>`citation=true` 表示原检索命中，分配 `[n]` 编号并进入 citations；
 * `false` 表示邻接扩展带入的上下文，只进入 prompt，不作为引用来源。</p>
 *
 * @param chunkId   切片 ID
 * @param docId     文档 ID
 * @param source    来源文件名
 * @param chunkIndex 切片序号
 * @param content   切片正文
 * @param score     引用分（重排分优先，否则向量分）；上下文片段为 0
 * @param citation  是否为原命中（可引用）
 */
public record RagContextSegment(String chunkId, String docId, String source, int chunkIndex,
                                String content, double score, boolean citation) {

    /**
     * 由检索命中构造可引用片段。
     *
     * @param hit 检索命中
     * @return 可引用片段
     */
    public static RagContextSegment fromHit(RagRetrievedChunk hit) {
        double finalScore = Objects.isNull(hit.rerankScore()) ? hit.score() : hit.rerankScore();
        return new RagContextSegment(hit.chunkId(), hit.docId(), hit.source(), hit.chunkIndex(),
                hit.content(), finalScore, true);
    }
}
