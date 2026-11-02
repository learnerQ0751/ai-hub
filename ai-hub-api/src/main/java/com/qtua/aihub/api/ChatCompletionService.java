package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.ChatCompletionStreamResDTO;

/**
 * 聊天补全应用服务契约。
 */
public interface ChatCompletionService {

    /**
     * 处理一次聊天补全请求（非流式）。
     *
     * @param reqDTO 原始请求
     * @return 原始响应
     */
    ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO);

    /**
     * 处理一次流式聊天补全请求。
     *
     * @param reqDTO 原始请求（stream=true）
     * @return SSE data 流与计量上下文
     */
    ChatCompletionStreamResDTO chatStream(ChatCompletionReqDTO reqDTO);
}
