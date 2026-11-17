package com.qtua.aihub.console.controller;

import com.qtua.aihub.api.McpServerService;
import com.qtua.aihub.api.model.req.McpServerRegisterReqDTO;
import com.qtua.aihub.api.model.res.McpRuntimeStatsResDTO;
import com.qtua.aihub.api.model.res.McpServerResDTO;
import com.qtua.aihub.service.admin.McpRuntimeStatsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP Server 注册与发现入口。
 *
 * <p>全部接口属于管理面，仅 master key 可访问（AuthFilter 拦截）。</p>
 */
@RestController
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpRuntimeStatsService mcpRuntimeStatsService;

    /**
     * 构造控制器。
     *
     * @param mcpServerService       MCP Server 服务
     * @param mcpRuntimeStatsService 运行时统计服务
     */
    public McpServerController(McpServerService mcpServerService,
                               McpRuntimeStatsService mcpRuntimeStatsService) {
        this.mcpServerService = mcpServerService;
        this.mcpRuntimeStatsService = mcpRuntimeStatsService;
    }

    /**
     * 注册 MCP Server（注册即探活）。
     *
     * @param reqDTO 注册参数
     * @return 201 与注册结果
     */
    @PostMapping(path = "/v1/admin/mcp/servers",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpServerResDTO> register(@RequestBody McpServerRegisterReqDTO reqDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mcpServerService.register(reqDTO));
    }

    /**
     * 查询全部 MCP Server。
     *
     * @return 服务器列表
     */
    @GetMapping(path = "/v1/admin/mcp/servers", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<McpServerResDTO> list() {
        return mcpServerService.list();
    }

    /**
     * 注销 MCP Server。
     *
     * @param serverId 对外服务器 ID
     * @return 204
     */
    @DeleteMapping(path = "/v1/admin/mcp/servers/{serverId}")
    public ResponseEntity<Void> remove(@PathVariable("serverId") String serverId) {
        mcpServerService.remove(serverId);
        return ResponseEntity.noContent().build();
    }

    /**
     * MCP 客户端运行时统计（管理面）。
     *
     * @return 活跃子进程/进行中请求计数
     */
    @GetMapping(path = "/v1/admin/mcp/runtime/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public McpRuntimeStatsResDTO runtimeStats() {
        return mcpRuntimeStatsService.stats();
    }

    /**
     * 手动重新探活。
     *
     * @param serverId 对外服务器 ID
     * @return 探活后的最新视图
     */
    @PostMapping(path = "/v1/admin/mcp/servers/{serverId}/reprobe",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public McpServerResDTO reprobe(@PathVariable("serverId") String serverId) {
        return mcpServerService.reprobe(serverId);
    }
}
