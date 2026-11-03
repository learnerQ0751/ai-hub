package com.qtua.aihub.common.mcp;

/**
 * 聚合后的工具描述（带 Server 归属）。
 *
 * <p>对外唯一名 {@code exposedName} = {@code {serverName}__{toolName}}；
 * 上游原始名保留在 {@code toolName}，调用时按原始名转发（REQ-2.3）。</p>
 *
 * @param serverId     所属 Server ID
 * @param serverName   所属 Server 名
 * @param tenantId     租户 ID（可空）
 * @param toolName     上游原始工具名
 * @param exposedName  对外唯一工具名
 * @param description  描述
 * @param inputSchema  输入 schema（JSON 文本）
 */
public record McpToolDescriptor(
        String serverId,
        String serverName,
        String tenantId,
        String toolName,
        String exposedName,
        String description,
        String inputSchema) {

    /**
     * 构建描述（自动拼接对外名）。
     *
     * @param serverId    所属 Server ID
     * @param serverName  所属 Server 名
     * @param tenantId    租户 ID
     * @param toolName    原始工具名
     * @param description 描述
     * @param inputSchema schema JSON
     * @return 聚合描述
     */
    public static McpToolDescriptor of(String serverId, String serverName, String tenantId,
                                       String toolName, String description, String inputSchema) {
        return new McpToolDescriptor(serverId, serverName, tenantId, toolName,
                serverName + "__" + toolName, description, inputSchema);
    }
}
