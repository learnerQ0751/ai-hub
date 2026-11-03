package com.qtua.aihub.api.model.req;

import java.util.List;
import java.util.Objects;

/**
 * 注册 MCP Server 请求。
 *
 * @param name        服务器名（必填；唯一；不得包含工具分隔符 "__"）
 * @param transport   传输方式：http/stdio（sse 暂不支持）
 * @param endpoint    HTTP 端点（transport=http 时必填）
 * @param command     stdio 启动命令（transport=stdio 时必填）
 * @param args        stdio 命令参数
 * @param tenantId    租户 ID（可选）
 * @param description 描述（可选）
 */
public record McpServerRegisterReqDTO(
        String name,
        String transport,
        String endpoint,
        String command,
        List<String> args,
        String tenantId,
        String description) {

    /**
     * 防御性复制参数列表。
     */
    public McpServerRegisterReqDTO {
        args = Objects.isNull(args) ? List.of() : List.copyOf(args);
    }
}
