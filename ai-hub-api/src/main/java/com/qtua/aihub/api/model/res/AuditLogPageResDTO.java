package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 审计分页（REQ-4.5）。
 *
 * @param items   当前页
 * @param count   条数
 * @param hasMore 是否还有下一页
 */
public record AuditLogPageResDTO(List<AuditLogResDTO> items, int count,
                                 @JsonProperty("has_more") boolean hasMore) {

    /**
     * 防御性复制。
     */
    public AuditLogPageResDTO {
        items = Objects.isNull(items) ? List.of() : List.copyOf(items);
    }
}
