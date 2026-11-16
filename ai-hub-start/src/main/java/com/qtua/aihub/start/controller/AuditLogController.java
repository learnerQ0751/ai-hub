package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.AuditQueryService;
import com.qtua.aihub.api.model.res.AuditLogPageResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询入口（REQ-4.5，仅 master）。
 */
@RestController
public class AuditLogController {

    private final AuditQueryService auditQueryService;

    /**
     * 构造控制器。
     *
     * @param auditQueryService 审计查询服务
     */
    public AuditLogController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    /**
     * 分页查询审计。
     *
     * @param tenantId 租户
     * @param action   动作
     * @param target   目标
     * @param from     起始时间
     * @param to       结束时间
     * @param limit    每页条数
     * @param offset   偏移
     * @return 分页
     */
    @GetMapping(path = "/v1/audit-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuditLogPageResDTO query(
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "target", required = false) String target,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return auditQueryService.query(tenantId, action, target, from, to, limit, offset);
    }
}
