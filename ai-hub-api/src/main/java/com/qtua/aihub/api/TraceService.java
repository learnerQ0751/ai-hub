package com.qtua.aihub.api;

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
}
