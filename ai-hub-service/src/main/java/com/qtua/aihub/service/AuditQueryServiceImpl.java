package com.qtua.aihub.service;

import com.qtua.aihub.api.AuditQueryService;
import com.qtua.aihub.api.model.res.AuditLogPageResDTO;
import com.qtua.aihub.api.model.res.AuditLogResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.manager.audit.AuditLogManager;
import com.qtua.aihub.manager.model.AuditLogBO;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 审计查询实现（REQ-4.5）。
 *
 * <p>入口由 AuthFilter 限制为 master；分页上限 200，时间参数为 ISO-8601。</p>
 */
@Service
public class AuditQueryServiceImpl implements AuditQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AuditLogManager auditLogManager;

    /**
     * 构造服务。
     *
     * @param auditLogManager 审计门面
     */
    public AuditQueryServiceImpl(AuditLogManager auditLogManager) {
        this.auditLogManager = auditLogManager;
    }

    @Override
    public AuditLogPageResDTO query(String tenantId, String action, String target, String from,
                                    String to, int limit, int offset) {
        int pageSize = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        int pageOffset = Math.max(0, offset);
        List<AuditLogBO> fetched = auditLogManager.findPage(trim(tenantId), trim(action),
                trim(target), parseInstant(from, "from"), parseInstant(to, "to"),
                pageSize + 1, pageOffset);
        List<AuditLogResDTO> items = fetched.stream().limit(pageSize).map(this::toDTO).toList();
        return new AuditLogPageResDTO(items, items.size(), fetched.size() > pageSize);
    }

    private AuditLogResDTO toDTO(AuditLogBO audit) {
        return new AuditLogResDTO(audit.id(), audit.tenantId(), audit.actor(), audit.action(),
                audit.target(), audit.before(), audit.after(), audit.traceId(),
                ObjectUtils.isEmpty(audit.createdAt()) ? null : audit.createdAt().toString());
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Instant parseInstant(String value, String param) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw ChatCompletionException.invalidRequest(
                    param + " 必须为 ISO-8601 时间（如 2026-11-16T00:00:00Z）", param);
        }
    }
}
