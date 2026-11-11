package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 清理记忆响应。
 *
 * @param deleted 实际物理删除条数
 * @param auditId 审计 ID；删除 0 条时为 null
 */
public record MemoryClearResDTO(
        int deleted,
        @JsonProperty("audit_id") String auditId) {
}
