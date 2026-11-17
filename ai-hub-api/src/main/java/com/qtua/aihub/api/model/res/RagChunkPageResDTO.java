package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * RAG 切片分页。
 *
 * @param items   当前页（按 chunk_index 升序）
 * @param count   当前页条数
 * @param hasMore 是否还有下一页
 */
public record RagChunkPageResDTO(List<RagChunkResDTO> items, int count,
                                 @JsonProperty("has_more") boolean hasMore) {

    /**
     * 防御性复制。
     */
    public RagChunkPageResDTO {
        items = Objects.isNull(items) ? List.of() : List.copyOf(items);
    }
}
