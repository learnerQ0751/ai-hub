package com.qtua.aihub.common.mcp;

import java.util.List;
import java.util.Objects;

/**
 * 工具聚合结果。
 *
 * @param tools            聚合后的工具列表（已加 Server 前缀）
 * @param degradedServers  本次聚合失败/超时的 Server 名
 */
public record ToolAggregationResult(List<McpToolDescriptor> tools, List<String> degradedServers) {

    /**
     * 防御性复制集合。
     */
    public ToolAggregationResult {
        tools = Objects.isNull(tools) ? List.of() : List.copyOf(tools);
        degradedServers = Objects.isNull(degradedServers) ? List.of() : List.copyOf(degradedServers);
    }
}
