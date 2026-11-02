package com.qtua.aihub.service.gateway;

import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;

/**
 * 业务执行出口。
 *
 * <p>当前由 {@link CoreChatExecutor} 在本地调用 core；未来拆独立 LLM 服务时，
 * 可替换为 HTTP 实现（POST 内部接口），网关层无需改动。</p>
 */
public interface ChatExecutor {

    /**
     * 执行一次聊天补全。
     *
     * @param bo 聊天补全业务对象
     * @return 响应、用量与实际部署模型
     */
    ChatCompletionResultBO execute(ChatCompletionBO bo);
}
