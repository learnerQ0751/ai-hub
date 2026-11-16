package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.api.McpCallService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 工具调用入口（数据面）。
 *
 * <p>路径编码对外工具名：{@code /v1/mcp/tools/{server}__{tool}:call}；
 * 鉴权、RPM 限流与日志由 GatewayGovernanceFilter 统一完成。</p>
 */
@RestController
public class McpCallController {

    private final McpCallService mcpCallService;

    /**
     * 构造控制器。
     *
     * @param mcpCallService 调用代理服务
     */
    public McpCallController(McpCallService mcpCallService) {
        this.mcpCallService = mcpCallService;
    }

    /**
     * 调用 MCP 工具。
     *
     * @param toolName 对外工具名
     * @param body     请求体（{"arguments": {...}}，可空）
     * @return 上游 result JSON
     */
    @PostMapping(path = "/v1/mcp/tools/{toolName}:call",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> call(@PathVariable("toolName") String toolName,
                                       @RequestBody(required = false) String body) {
        return ResponseEntity.ok(mcpCallService.call(toolName, body));
    }
}
