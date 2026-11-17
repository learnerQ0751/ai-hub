package com.qtua.aihub.api;

import com.qtua.aihub.api.model.res.TraceListResDTO;
import com.qtua.aihub.api.model.res.TraceResDTO;

/**
 * 全链路 Trace 查询（REQ-4.2）。
 */
public interface TraceService {

    /**
     * 按 trace_id 查询分段事件。
     *
     * @param traceId 追踪 ID
     * @return 分段事件
     */
    TraceResDTO getTrace(String traceId);

    /**
     * Trace 列表：按 trace_id 聚合并按首个事件时间倒序分页（§7.1）。
     *
     * @param tenantId  租户（可空）
     * @param projectId 项目（可空）
     * @param keyId     Key 哈希（可空）
     * @param status    聚合后最大状态码（可空）
     * @param from      起始时间 ISO-8601（可空）
     * @param to        结束时间 ISO-8601（可空）
     * @param limit     每页条数
     * @param offset    偏移
     * @return 分页
     */
    TraceListResDTO listTraces(String tenantId, String projectId, String keyId, Integer status,
                               String from, String to, int limit, int offset);
}
