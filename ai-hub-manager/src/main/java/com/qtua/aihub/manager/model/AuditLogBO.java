package com.qtua.aihub.manager.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 审计记录业务对象（REQ-4.5）。
 *
 * @param id        记录 ID
 * @param tenantId  租户
 * @param actor     操作者（master/Key 哈希）
 * @param action    动作
 * @param target    目标
 * @param before    变更前快照
 * @param after     变更后快照
 * @param traceId   追踪 ID
 * @param createdAt 时间
 */
public record AuditLogBO(long id, String tenantId, String actor, String action, String target,
                         Map<String, Object> before, Map<String, Object> after, String traceId,
                         Instant createdAt) {

    /**
     * 防御性复制快照。
     */
    public AuditLogBO {
        before = Objects.isNull(before) ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(before));
        after = Objects.isNull(after) ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(after));
    }
}
