package com.qtua.aihub.service.gateway;

import com.qtua.aihub.core.ChatCompletionCore;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.springframework.stereotype.Component;

/**
 * 本地业务执行器：直接调用 {@link ChatCompletionCore}。
 *
 * <p>未来拆独立 LLM 服务时，用 HTTP 实现替换本类即可。</p>
 */
@Component
public class CoreChatExecutor implements ChatExecutor {

    private final ChatCompletionCore chatCompletionCore;

    /**
     * 构造执行器。
     *
     * @param chatCompletionCore 聊天补全业务编排
     */
    public CoreChatExecutor(ChatCompletionCore chatCompletionCore) {
        this.chatCompletionCore = chatCompletionCore;
    }

    /**
     * 执行一次聊天补全。
     *
     * @param bo 聊天补全业务对象
     * @return 响应、用量与实际部署模型
     */
    @Override
    public ChatCompletionResultBO execute(ChatCompletionBO bo) {
        return chatCompletionCore.chat(bo);
    }
}
