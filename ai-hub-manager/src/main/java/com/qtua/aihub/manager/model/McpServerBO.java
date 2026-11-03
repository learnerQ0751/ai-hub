package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.TransportType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * MCP Server 业务对象。
 *
 * @param serverId     对外服务器 ID（UUID）
 * @param name         服务器名（唯一，不含 "__"）
 * @param transport    传输方式
 * @param endpoint     HTTP/SSE 端点（原始值，对外返回前掩码）
 * @param command      stdio 启动命令
 * @param args         stdio 命令参数
 * @param tenantId     租户 ID（可空）
 * @param status       健康状态
 * @param lastHealthAt 最近探活时间
 * @param description  描述（存 metadata）
 * @param lastError    最近探活错误（存 metadata）
 * @param createdAt    创建时间
 */
public record McpServerBO(
        String serverId,
        String name,
        TransportType transport,
        String endpoint,
        String command,
        List<String> args,
        String tenantId,
        McpServerStatus status,
        Instant lastHealthAt,
        String description,
        String lastError,
        Instant createdAt) {

    /**
     * 防御性复制参数列表。
     */
    public McpServerBO {
        args = Objects.isNull(args) ? List.of() : List.copyOf(args);
    }

    /**
     * 返回带新健康状态的副本。
     *
     * @param newStatus    新状态
     * @param healthAt     探活时间
     * @param errorMessage 本次探活错误（成功时为 null）
     * @return 更新后的业务对象
     */
    public McpServerBO withHealth(McpServerStatus newStatus, Instant healthAt, String errorMessage) {
        return new McpServerBO(serverId, name, transport, endpoint, command, args, tenantId,
                newStatus, healthAt, description, errorMessage, createdAt);
    }
}
