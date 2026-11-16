package com.qtua.aihub.service.admin;

import com.qtua.aihub.api.model.res.McpRuntimeStatsResDTO;
import com.qtua.aihub.integration.mcp.McpServerProbe;
import com.qtua.aihub.integration.mcp.McpToolClient;
import com.qtua.aihub.integration.mcp.StdioProcessRegistry;
import org.springframework.stereotype.Service;

/**
 * MCP 客户端运行时统计服务（REQ-2.10）。
 *
 * <p>轻量观察性：活跃 stdio 子进程数、探活/工具调用的进行中请求数。
 * 第 4 期可观测性接入 Micrometer/Actuator 后作为补充指标。</p>
 */
@Service
public class McpRuntimeStatsService {

    private final StdioProcessRegistry stdioProcessRegistry;
    private final McpServerProbe mcpServerProbe;
    private final McpToolClient mcpToolClient;

    /**
     * 构造服务。
     *
     * @param stdioProcessRegistry stdio 子进程注册表
     * @param mcpServerProbe       探活器
     * @param mcpToolClient        工具客户端
     */
    public McpRuntimeStatsService(StdioProcessRegistry stdioProcessRegistry,
                                  McpServerProbe mcpServerProbe,
                                  McpToolClient mcpToolClient) {
        this.stdioProcessRegistry = stdioProcessRegistry;
        this.mcpServerProbe = mcpServerProbe;
        this.mcpToolClient = mcpToolClient;
    }

    /**
     * 采集运行时统计。
     *
     * @return 统计结果
     */
    public McpRuntimeStatsResDTO stats() {
        return new McpRuntimeStatsResDTO(stdioProcessRegistry.activeCount(),
                mcpServerProbe.inFlightHttpCount(), mcpToolClient.inFlightCount());
    }
}
