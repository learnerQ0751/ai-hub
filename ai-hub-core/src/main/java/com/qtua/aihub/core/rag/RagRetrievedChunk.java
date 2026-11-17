package com.qtua.aihub.core.rag;

/**
 * 一次检索命中的切片。
 *
 * <p>`score` 保持向量相似度语义（无向量路命中时为 0）；`rerankScore` 仅在关闭混合检索
 * 且启用规则重排时非空；`matchType` 与两路原始分数用于解释混合检索结果，
 * `rrfScore` 是融合后的排序分。Core 不依赖 api DTO，由 Service 转换为响应。</p>
 *
 * @param chunkId      切片 ID
 * @param docId        文档 ID
 * @param projectId    项目 ID
 * @param source       来源文件名
 * @param chunkIndex   切片序号
 * @param content      切片正文
 * @param score        向量相似度；仅字面命中为 0
 * @param rerankScore  规则重排分数；未重排为 null
 * @param matchType    命中路径：vector / keyword / both
 * @param vectorScore  向量路原始分数；未命中为 null
 * @param keywordScore 字面路原始分数（词项覆盖率）；未命中为 null
 * @param rrfScore     RRF 融合分；未启用混合检索为 null
 */
public record RagRetrievedChunk(String chunkId, String docId, String projectId, String source,
                                int chunkIndex, String content, double score,
                                Double rerankScore, String matchType, Double vectorScore,
                                Double keywordScore, Double rrfScore) {

    /**
     * 兼容构造器：仅向量结果（三期行为）。
     */
    public RagRetrievedChunk(String chunkId, String docId, String projectId, String source,
                             int chunkIndex, String content, double score,
                             Double rerankScore) {
        this(chunkId, docId, projectId, source, chunkIndex, content, score, rerankScore,
                "vector", score, null, null);
    }
}
