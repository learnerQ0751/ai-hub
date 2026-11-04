package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;

/**
 * Chat + MCP 工具编排服务（REQ-2.8）。
 *
 * <p>请求中的 {@code tools:[{"type":"mcp","server_name":"..."}]} 会被展开为该
 * Server 的工具目录；模型返回 tool_calls 时由网关执行 MCP 工具并回填，循环调用模型。</p>
 */
public interface ChatMcpService {

    /**
     * 执行带 MCP 工具的聊天补全（非流式）。
     *
     * @param reqDTO 原始请求（含 mcp tools 声明）
     * @return 最终响应（usage 为各轮累计）
     */
    ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO);
}
