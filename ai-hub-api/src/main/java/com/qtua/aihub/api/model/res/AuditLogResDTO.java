package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 审计记录（REQ-4.5，before/after 已脱敏）。
 *
 * @param id        记录 ID
 * @param tenantId  租户
 * @param actor     操作者
 * @param action    动作
 * @param target    目标
 * @param before    变更前
 * @param after     变更后
 * @param traceId   追踪 ID
 * @param createdAt 时间
 */
public record AuditLogResDTO(long id,
                             @JsonProperty("tenant_id") String tenantId,
                             String actor, String action, String target,
                             Map<String, Object> before, Map<String, Object> after,
                             @JsonProperty("trace_id") String traceId,
                             @JsonProperty("created_at") String createdAt) {

    /**
     * 防御性复制快照。
     */
    public AuditLogResDTO {
        before = java.util.Objects.isNull(before) ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(before));
        after = java.util.Objects.isNull(after) ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(after));
    }
}
