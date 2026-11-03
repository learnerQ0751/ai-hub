package com.qtua.aihub.api.model.res;

import java.util.List;
import java.util.Objects;

/**
 * MCP 工具目录响应。
 *
 * @param tools           工具列表（可按 server_id/tenant_id 过滤）
 * @param degradedServers 本次聚合中失败/超时、已降级的 Server 名
 * @param message         结果说明
 */
public record McpToolListResDTO(
        List<McpToolResDTO> tools,
        List<String> degradedServers,
        String message) {

    /**
     * 防御性复制集合。
     */
    public McpToolListResDTO {
        tools = Objects.isNull(tools) ? List.of() : List.copyOf(tools);
        degradedServers = Objects.isNull(degradedServers) ? List.of() : List.copyOf(degradedServers);
    }
}
