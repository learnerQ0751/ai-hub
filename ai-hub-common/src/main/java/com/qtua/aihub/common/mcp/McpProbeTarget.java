package com.qtua.aihub.common.mcp;

import java.util.List;
import java.util.Objects;

/**
 * MCP 探活目标（跨层传递的连接信息）。
 *
 * <p>integration 层不能依赖 manager 的 BO，因此探活入参收敛为 common 领域对象；
 * service 层负责从 BO 转换。</p>
 *
 * @param name      服务器名（日志用）
 * @param transport 传输方式
 * @param endpoint  HTTP/SSE 端点
 * @param command   stdio 启动命令
 * @param args      stdio 命令参数
 */
public record McpProbeTarget(
        String name,
        TransportType transport,
        String endpoint,
        String command,
        List<String> args) {

    /**
     * 防御性复制参数列表。
     */
    public McpProbeTarget {
        args = Objects.isNull(args) ? List.of() : List.copyOf(args);
    }
}
