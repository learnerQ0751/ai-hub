package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.TraceService;
import com.qtua.aihub.api.model.res.TraceResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全链路 Trace 查询入口（REQ-4.2）。
 *
 * <p>任意有效 Key 可查；虚拟 Key 只能看到绑定租户的事件（Service 过滤）。</p>
 */
@RestController
public class TraceController {

    private final TraceService traceService;

    /**
     * 构造控制器。
     *
     * @param traceService Trace 服务
     */
    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 按 trace_id 查询分段事件。
     *
     * @param traceId 追踪 ID（响应头 X-Trace-Id）
     * @return 分段事件
     */
    @GetMapping(path = "/v1/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TraceResDTO get(@PathVariable("traceId") String traceId) {
        return traceService.getTrace(traceId);
    }
}
