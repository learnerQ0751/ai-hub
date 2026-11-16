package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.api.McpToolService;
import com.qtua.aihub.api.model.res.McpToolListResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MCP 工具目录入口（数据面，任意 Key 可访问）。
 *
 * <p>工具目录来自 ACTIVE Server 的聚合快照（TTL 60s），支持按 Server/租户过滤与手动刷新。</p>
 */
@RestController
public class McpToolController {

    private final McpToolService mcpToolService;

    /**
     * 构造控制器。
     *
     * @param mcpToolService 工具发现服务
     */
    public McpToolController(McpToolService mcpToolService) {
        this.mcpToolService = mcpToolService;
    }

    /**
     * 查询工具目录。
     *
     * @param serverId Server ID 或名称（可选）
     * @param tenantId 租户 ID（可选）
     * @return 工具目录
     */
    @GetMapping(path = "/v1/mcp/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    public McpToolListResDTO list(
            @RequestParam(name = "server_id", required = false) String serverId,
            @RequestParam(name = "tenant_id", required = false) String tenantId) {
        return mcpToolService.list(serverId, tenantId);
    }

    /**
     * 手动刷新工具目录。
     *
     * @return 操作结果
     */
    @PostMapping(path = "/v1/mcp/tools/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> refresh() {
        mcpToolService.refresh();
        return Map.of("message", "工具目录已刷新");
    }
}
