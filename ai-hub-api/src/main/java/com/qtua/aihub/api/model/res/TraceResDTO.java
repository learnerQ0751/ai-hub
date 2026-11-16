package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Trace 查询响应（REQ-4.2）。
 *
 * @param traceId 追踪 ID
 * @param count   事件数
 * @param events  分段事件（时间顺序）
 */
public record TraceResDTO(@JsonProperty("trace_id") String traceId, int count,
                          List<TraceEventResDTO> events) {

    /**
     * 防御性复制。
     */
    public TraceResDTO {
        events = Objects.isNull(events) ? List.of() : List.copyOf(events);
    }
}
