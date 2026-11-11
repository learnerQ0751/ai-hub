package com.qtua.aihub.core.rag;

import com.qtua.aihub.manager.model.RagDocumentBO;

/**
 * 文档接入结果。
 *
 * @param document   文档
 * @param duplicated 是否命中 hash 去重并返回已有文档
 * @param overridden 是否按覆盖策略替换了旧文档
 */
public record RagIngestResult(RagDocumentBO document, boolean duplicated, boolean overridden) {
}
