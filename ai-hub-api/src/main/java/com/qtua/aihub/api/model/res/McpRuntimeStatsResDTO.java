package com.qtua.aihub.api.model.res;

/**
 * MCP 客户端运行时统计（REQ-2.10）。
 *
 * @param activeStdioProcesses 本实例活跃 stdio 子进程数
 * @param inFlightProbeHttp    进行中的 HTTP 探活请求数
 * @param inFlightToolCalls    进行中的 MCP 工具调用数
 */
public record McpRuntimeStatsResDTO(
        int activeStdioProcesses,
        int inFlightProbeHttp,
        int inFlightToolCalls) {
}
