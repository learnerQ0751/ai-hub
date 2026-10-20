package com.qtua.aihub.api;

import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;

/**
 * 聊天补全应用服务契约。
 */
public interface ChatCompletionService {

    /**
     * 处理一次聊天补全请求。
     *
     * @param reqDTO 原始请求
     * @return 原始响应
     */
    ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO);
}
