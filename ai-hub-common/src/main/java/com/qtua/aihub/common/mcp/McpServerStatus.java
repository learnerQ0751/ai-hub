package com.qtua.aihub.common.mcp;

/**
 * MCP Server 健康状态。
 *
 * <p>注册后先落 {@code DOWN}，探活成功置 {@code ACTIVE}；探活失败保持 {@code DOWN}
 * 并把错误写入 metadata.lastError，供列表查询与手动重探。</p>
 */
public enum McpServerStatus {

    /** 探活成功、可用。 */
    ACTIVE,

    /** 探活失败或未探活。 */
    DOWN
}
