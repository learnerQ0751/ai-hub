package com.qtua.aihub.common.rag;

/**
 * 文档/切片索引状态。
 *
 * <p>正常情况下 PENDING → PROCESSING → READY；任何向量化或存储异常进入 FAILED。
 * FAILED 与 PROCESSING 均可通过 reindex 重试。</p>
 */
public enum RagIndexStatus {

    /** 待索引。 */
    PENDING,
    /** 索引中。 */
    PROCESSING,
    /** 索引完成，可检索。 */
    READY,
    /** 索引失败，可重试。 */
    FAILED
}
