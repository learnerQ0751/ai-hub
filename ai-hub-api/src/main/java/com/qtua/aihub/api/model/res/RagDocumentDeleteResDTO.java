package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 文档删除结果。
 *
 * @param deleted        是否删除了文档
 * @param chunksDeleted  删除的切片数
 * @param vectorsDeleted 删除的向量数
 */
public record RagDocumentDeleteResDTO(
        boolean deleted,
        @JsonProperty("chunks_deleted") int chunksDeleted,
        @JsonProperty("vectors_deleted") int vectorsDeleted) {
}
