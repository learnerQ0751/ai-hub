package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 管理台项目分页。
 *
 * @param items   当前页
 * @param count   当前页条数
 * @param hasMore 是否还有下一页
 */
public record AdminProjectPageResDTO(List<AdminProjectResDTO> items, int count,
                                     @JsonProperty("has_more") boolean hasMore) {

    /**
     * 防御性复制。
     */
    public AdminProjectPageResDTO {
        items = Objects.isNull(items) ? List.of() : List.copyOf(items);
    }
}
