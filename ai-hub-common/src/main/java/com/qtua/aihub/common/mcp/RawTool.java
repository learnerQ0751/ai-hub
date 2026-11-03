package com.qtua.aihub.common.mcp;

/**
 * MCP 上游返回的原始工具定义（未加 Server 前缀）。
 *
 * @param name        上游工具名
 * @param description 描述
 * @param inputSchema 输入 schema（JSON 文本，原样透传）
 */
public record RawTool(String name, String description, String inputSchema) {
}
