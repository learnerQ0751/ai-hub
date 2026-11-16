package com.qtua.aihub.service;

import com.qtua.aihub.api.TraceService;
import com.qtua.aihub.api.model.res.TraceEventResDTO;
import com.qtua.aihub.api.model.res.TraceResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.manager.model.TraceEventBO;
import com.qtua.aihub.manager.trace.TraceEventManager;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

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
