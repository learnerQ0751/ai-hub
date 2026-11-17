package com.qtua.aihub.console.controller;

import com.qtua.aihub.api.TraceService;
import com.qtua.aihub.api.model.res.TraceListResDTO;
import com.qtua.aihub.api.model.res.TraceResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Trace 列表：按 trace_id 聚合分页（§7.1）。
     *
     * @param tenantId  租户
     * @param projectId 项目
     * @param keyId     Key 哈希
     * @param status    聚合后最大状态码
     * @param from      起始时间 ISO-8601
     * @param to        结束时间 ISO-8601
     * @param limit     每页条数
     * @param offset    偏移
     * @return 分页
     */
    @GetMapping(path = "/v1/admin/traces", produces = MediaType.APPLICATION_JSON_VALUE)
    public TraceListResDTO list(
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "key_id", required = false) String keyId,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return traceService.listTraces(tenantId, projectId, keyId, status, from, to,
                limit, offset);
    }

    /**
     * 按 trace_id 查询分段事件。
     *
     * @param traceId 追踪 ID（响应头 X-Trace-Id）
     * @return 分段事件
     */
    @GetMapping(path = "/v1/admin/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TraceResDTO get(@PathVariable("traceId") String traceId) {
        return traceService.getTrace(traceId);
    }
}
