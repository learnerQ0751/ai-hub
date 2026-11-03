package com.qtua.aihub.api;

/**
 * MCP 工具调用代理服务（数据面）。
 *
 * <p>解析对外工具名 → JSON-RPC {@code tools/call} → 原样返回结果；
 * 治理（鉴权/RPM/日志）由 GatewayGovernanceFilter 统一完成。</p>
 */
public interface McpCallService {

    /**
     * 调用工具。
     *
     * @param toolName    对外工具名（{serverName}__{toolName}）
     * @param requestBody 请求体 JSON（含 arguments 对象，可空）
     * @return 上游 result JSON（大结果已截断）
     */
    String call(String toolName, String requestBody);
}
