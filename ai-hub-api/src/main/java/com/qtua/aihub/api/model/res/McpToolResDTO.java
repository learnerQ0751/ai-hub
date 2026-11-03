package com.qtua.aihub.api.model.res;

/**
 * MCP 工具视图。
 *
 * @param name         对外唯一名（{serverName}__{toolName}）
 * @param originalName 上游原始工具名（调用时使用）
 * @param description  描述
 * @param inputSchema  输入 schema（JSON 文本）
 * @param serverId     所属 Server ID
 * @param serverName   所属 Server 名
 * @param tenantId     租户 ID
 */
public record McpToolResDTO(
        String name,
        String originalName,
        String description,
        String inputSchema,
        String serverId,
        String serverName,
        String tenantId) {
}
