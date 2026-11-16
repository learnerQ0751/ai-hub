package com.qtua.aihub.service.shared;

import com.qtua.aihub.common.audit.AuditRecorder;
import com.qtua.aihub.common.tenant.TenantContextHolder;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.manager.audit.AuditLogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Map;

/**
 * 审计记录实现（REQ-4.5）。
 *
 * <p>actor 从租户上下文派生：master 记 "master"，虚拟 Key 记哈希；trace_id 取 MDC。
 * 写失败只打日志，不影响已完成的治理动作（可重放/人工补录）。</p>
 */
@Component
public class AuditRecorderImpl implements AuditRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(AuditRecorderImpl.class);
    private static final String ACTOR_MASTER = "master";
    private static final String ACTOR_UNKNOWN = "unknown";

    private final AuditLogManager auditLogManager;

    /**
     * 构造记录器。
     *
     * @param auditLogManager 审计数据门面
     */
    public AuditRecorderImpl(AuditLogManager auditLogManager) {
        this.auditLogManager = auditLogManager;
    }

    @Override
    public void record(String tenantId, String action, String target, Map<String, Object> before,
                       Map<String, Object> after) {
        try {
            TenantIdentity identity = TenantContextHolder.current();
            String actor = ACTOR_UNKNOWN;
            if (!ObjectUtils.isEmpty(identity)) {
                actor = identity.master() ? ACTOR_MASTER
                        : (ObjectUtils.isEmpty(identity.keyHash()) ? ACTOR_UNKNOWN
                        : identity.keyHash());
            }
            String traceId = org.slf4j.MDC.get("trace_id");
            auditLogManager.append(tenantId, actor, action, target, before, after, traceId);
        } catch (RuntimeException ex) {
            LOG.error("failed to persist audit action={} target={}", action, target, ex);
        }
    }
}
