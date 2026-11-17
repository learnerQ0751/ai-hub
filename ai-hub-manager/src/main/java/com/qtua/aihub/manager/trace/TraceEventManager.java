package com.qtua.aihub.manager.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.trace.TraceEvent;
import com.qtua.aihub.dal.mapper.TraceEventMapper;
import com.qtua.aihub.dal.model.TraceEventDO;
import com.qtua.aihub.dal.model.TraceSummaryDO;
import com.qtua.aihub.manager.model.TraceEventBO;
import com.qtua.aihub.manager.model.TraceSummaryBO;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Trace 事件数据门面（REQ-4.2）。
 *
 * <p>负责 payload JSON 编解码与 DO↔BO；写入/查询策略在 service。</p>
 */
@Component
public class TraceEventManager {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final TraceEventMapper traceEventMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造门面。
     *
     * @param traceEventMapper Trace Mapper
     * @param objectMapper     JSON 编解码
     */
    public TraceEventManager(TraceEventMapper traceEventMapper, ObjectMapper objectMapper) {
        this.traceEventMapper = traceEventMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 追加一条事件。
     *
     * @param event 事件
     */
    public void append(TraceEvent event) {
        TraceEventDO dataObject = new TraceEventDO();
        dataObject.setTraceId(event.traceId());
        dataObject.setTenantId(event.tenantId());
        dataObject.setProjectId(event.projectId());
        dataObject.setKeyId(event.keyHash());
        dataObject.setSegment(event.segment());
        dataObject.setName(event.name());
        dataObject.setStatus(event.status());
        dataObject.setLatencyMs(event.latencyMs());
        dataObject.setPayload(writePayload(event.payload()));
        dataObject.setCreatedAt(event.createdAt());
        traceEventMapper.insert(dataObject);
    }

    /**
     * 按 trace_id 查询（时间顺序）。
     *
     * @param traceId 追踪 ID
     * @return 事件列表
     */
    public List<TraceEventBO> findByTraceId(String traceId) {
        return traceEventMapper.selectByTraceId(traceId).stream().map(this::toBO).toList();
    }

    /**
     * Trace 列表：按 trace_id 聚合分页（§7.1）。
     *
     * @param tenantId  租户（可空）
     * @param projectId 项目（可空）
     * @param keyId     Key 哈希（可空）
     * @param status    聚合后最大状态码（可空）
     * @param from      起始时间（可空）
     * @param to        结束时间（可空）
     * @param limit     每页条数
     * @param offset    偏移
     * @return 聚合摘要
     */
    public List<TraceSummaryBO> listTraces(String tenantId, String projectId, String keyId,
                                           Integer status, Instant from, Instant to,
                                           int limit, int offset) {
        return traceEventMapper.selectTracePage(tenantId, projectId, keyId, status, from, to,
                        Math.max(1, limit), Math.max(0, offset)).stream()
                .map(this::toSummaryBO).toList();
    }

    private TraceSummaryBO toSummaryBO(TraceSummaryDO dataObject) {
        return new TraceSummaryBO(dataObject.getTraceId(), dataObject.getTenantId(),
                dataObject.getProjectId(), dataObject.getKeyId(), dataObject.getStartedAt(),
                dataObject.getFinishedAt(), dataObject.getEventCount(), dataObject.getStatus());
    }

    private TraceEventBO toBO(TraceEventDO dataObject) {
        return new TraceEventBO(dataObject.getTraceId(), dataObject.getTenantId(),
                dataObject.getProjectId(), dataObject.getKeyId(), dataObject.getSegment(),
                dataObject.getName(), dataObject.getStatus(), dataObject.getLatencyMs(),
                readPayload(dataObject.getPayload()), dataObject.getCreatedAt());
    }

    private String writePayload(Map<String, Object> payload) {
        if (ObjectUtils.isEmpty(payload)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Map<String, Object> readPayload(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
