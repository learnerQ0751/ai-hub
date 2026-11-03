package com.qtua.aihub.integration.mcp;

/**
 * MCP 工具调用/发现超时（用于映射 504）。
 */
public class McpToolTimeoutException extends RuntimeException {

    /**
     * 构造超时异常。
     *
     * @param message 描述
     * @param cause   原始异常
     */
    public McpToolTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
