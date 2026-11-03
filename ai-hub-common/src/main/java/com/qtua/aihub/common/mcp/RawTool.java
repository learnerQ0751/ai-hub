package com.qtua.aihub.common.mcp;

/**
 * MCP 上游返回的原始工具定义（未加 Server 前缀）。
 *
 * @param name        上游工具名
 * @param description 描述
 * @param inputSchema 输入 schema（JSON 文本，原样透传）
 * @param idempotent  是否幂等（上游 _meta.idempotent，用于调用失败重试策略）
 */
public record RawTool(String name, String description, String inputSchema, boolean idempotent) {
}
