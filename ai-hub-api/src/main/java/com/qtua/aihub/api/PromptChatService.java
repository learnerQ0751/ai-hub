package com.qtua.aihub.api;

import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.PromptChatReqDTO;
import com.qtua.aihub.api.model.req.PromptRenderReqDTO;
import com.qtua.aihub.api.model.res.PromptChatResDTO;
import com.qtua.aihub.api.model.res.PromptRenderResDTO;

/**
 * Prompt 数据面服务：渲染（不调模型）与对话（渲染 → chat 链路）。
 */
public interface PromptChatService {

    /**
     * 渲染模板（不调用模型）。
     *
     * @param name   模板名
     * @param reqDTO 渲染参数
     * @return 渲染结果
     */
    PromptRenderResDTO render(String name, PromptRenderReqDTO reqDTO);

    /**
     * 渲染并调用模型。
     *
     * @param name     模板名
     * @param reqDTO   对话参数
     * @param auth     认证上下文（内部调用透传）
     * @param requestId 请求追踪 ID
     * @return 对话结果（含用量与模板版本）
     */
    PromptChatResDTO chat(String name, PromptChatReqDTO reqDTO, KeyAuthDTO auth, String requestId);
}
