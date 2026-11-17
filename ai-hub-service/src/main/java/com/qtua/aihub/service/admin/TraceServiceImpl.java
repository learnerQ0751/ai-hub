package com.qtua.aihub.service.admin;

import com.qtua.aihub.api.TraceService;
import com.qtua.aihub.api.model.res.TraceEventResDTO;
import com.qtua.aihub.api.model.res.TraceListResDTO;
import com.qtua.aihub.api.model.res.TraceResDTO;
import com.qtua.aihub.api.model.res.TraceSummaryResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.manager.model.TraceEventBO;
import com.qtua.aihub.manager.model.TraceSummaryBO;
import com.qtua.aihub.manager.trace.TraceEventManager;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * Trace 查询实现（REQ-4.2）。
 *
 * <p>master 可查任意 trace；虚拟 Key 只能看到绑定租户的事件。无匹配统一 404，
 * 不泄露 trace 是否存在。</p>
 */
@Service
public class TraceServiceImpl implements TraceService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final TraceEventManager traceEventManager;
    private final TenantScopeGuard tenantScopeGuard;

    /**
     * 构造服务。
     *
     * @param traceEventManager Trace 数据门面
     * @param tenantScopeGuard  租户守卫
     */
    public TraceServiceImpl(TraceEventManager traceEventManager,
                            TenantScopeGuard tenantScopeGuard) {
        this.traceEventManager = traceEventManager;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @Override
    public TraceResDTO getTrace(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            throw ChatCompletionException.invalidRequest("trace_id 不能为空", "trace_id");
        }
        String normalized = traceId.trim();
        TenantIdentity identity = tenantScopeGuard.currentIdentity();
        List<TraceEventResDTO> events = traceEventManager.findByTraceId(normalized).stream()
                .filter(event -> visible(event, identity))
                .map(this::toResDTO)
                .toList();
        if (events.isEmpty()) {
            throw ChatCompletionException.traceNotFound(normalized);
        }
        return new TraceResDTO(normalized, events.size(), events);
    }

    @Override
    public TraceListResDTO listTraces(String tenantId, String projectId, String keyId,
                                      Integer status, String from, String to, int limit,
                                      int offset) {
        String effectiveTenant = resolveTenantFilter(tenantId);
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        if (!Objects.isNull(status) && status < 0) {
            throw ChatCompletionException.invalidRequest("status 必须为非负整数", "status");
        }
        int pageSize = resolveLimit(limit);
        int pageOffset = Math.max(0, offset);
        List<TraceSummaryBO> fetched = traceEventManager.listTraces(effectiveTenant,
                trimToNull(projectId), trimToNull(keyId), status, fromInstant, toInstant,
                pageSize + 1, pageOffset);
        List<TraceSummaryResDTO> items = fetched.stream().limit(pageSize)
                .map(this::toSummaryResDTO).toList();
        return new TraceListResDTO(items, items.size(), fetched.size() > pageSize);
    }

    private String resolveTenantFilter(String tenantId) {
        TenantIdentity identity = tenantScopeGuard.currentIdentity();
        if (ObjectUtils.isEmpty(identity) || identity.master()) {
            return trimToNull(tenantId);
        }
        String bound = trimToNull(identity.tenantId());
        if (Objects.isNull(bound)) {
            throw ChatCompletionException.tenantAccessDenied(
                    "当前 API Key 未绑定租户，禁止访问租户资源", tenantId, null);
        }
        if (!Objects.isNull(trimToNull(tenantId)) && !trimToNull(tenantId).equals(bound)) {
            throw ChatCompletionException.tenantAccessDenied(
                    "请求租户与 API Key 绑定租户不一致", tenantId, null);
        }
        return bound;
    }

    private TraceSummaryResDTO toSummaryResDTO(TraceSummaryBO summary) {
        return new TraceSummaryResDTO(summary.traceId(), summary.tenantId(), summary.projectId(),
                summary.keyId(), iso(summary.startedAt()), iso(summary.finishedAt()),
                summary.eventCount(), summary.status());
    }

    private int resolveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String iso(Instant instant) {
        return Objects.isNull(instant) ? null : instant.toString();
    }

    private boolean visible(TraceEventBO event, TenantIdentity identity) {
        if (ObjectUtils.isEmpty(identity) || identity.master()) {
            return true;
        }
        return Objects.equals(identity.tenantId(), event.tenantId());
    }

    private TraceEventResDTO toResDTO(TraceEventBO event) {
        return new TraceEventResDTO(event.segment(), event.name(), event.status(),
                event.latencyMs(), event.payload(),
                Objects.isNull(event.createdAt()) ? null : event.createdAt().toString());
    }
}
