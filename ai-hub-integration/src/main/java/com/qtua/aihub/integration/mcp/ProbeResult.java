package com.qtua.aihub.integration.mcp;

/**
 * MCP 探活结果。
 *
 * @param healthy 是否健康（initialize 成功）
 * @param error   失败原因（成功时为 null）
 */
public record ProbeResult(boolean healthy, String error) {

    /**
     * 构造成功结果。
     *
     * @return 健康结果
     */
    public static ProbeResult ok() {
        return new ProbeResult(true, null);
    }

    /**
     * 构造失败结果。
     *
     * @param error 失败原因（中文，写 metadata.lastError）
     * @return 不健康结果
     */
    public static ProbeResult fail(String error) {
        return new ProbeResult(false, error);
    }
}
