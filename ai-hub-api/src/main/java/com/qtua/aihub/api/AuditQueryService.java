package com.qtua.aihub.api;

import com.qtua.aihub.api.model.res.AuditLogPageResDTO;

/**
 * 审计查询（REQ-4.5，仅 master）。
 */
public interface AuditQueryService {

    /**
     * 分页查询审计。
     *
     * @param tenantId 租户（可空）
     * @param action   动作（可空）
     * @param target   目标（模糊，可空）
     * @param from     起始时间（ISO-8601，可空）
     * @param to       结束时间（ISO-8601，可空）
     * @param limit    每页条数
     * @param offset   偏移
     * @return 分页
     */
    AuditLogPageResDTO query(String tenantId, String action, String target, String from,
                             String to, int limit, int offset);
}
