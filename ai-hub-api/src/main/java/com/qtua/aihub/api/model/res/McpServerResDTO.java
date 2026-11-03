package com.qtua.aihub.api.model.res;

import java.time.Instant;
import java.util.List;

/**
 * MCP Server 响应视图。
 *
 * <p>{@code endpoint} 已做敏感信息掩码（userinfo / 敏感 query 值），
 * 原始值只存在于 DB 与 internal 探活链路。</p>
 *
 * @param serverId     对外服务器 ID
 * @param name         服务器名
 * @param transport    传输方式
 * @param endpoint     掩码后的端点
 * @param command      stdio 命令
 * @param args         stdio 参数
 * @param tenantId     租户 ID
 * @param status       健康状态（ACTIVE/DOWN）
 * @param lastHealthAt 最近探活时间
 * @param description  描述
 * @param lastError    最近探活错误
 * @param createdAt    创建时间
 */
public record McpServerResDTO(
        String serverId,
        String name,
        String transport,
        String endpoint,
        String command,
        List<String> args,
        String tenantId,
        String status,
        Instant lastHealthAt,
        String description,
        String lastError,
        Instant createdAt) {

    /**
     * 防御性复制参数列表，避免内部可变状态外泄。
     */
    public McpServerResDTO {
        args = java.util.Objects.isNull(args) ? List.of() : List.copyOf(args);
    }
}
