package com.qtua.aihub.api.model.res;

import com.qtua.aihub.common.prompt.PromptMessage;

import java.util.List;
import java.util.Objects;

/**
 * Prompt 渲染响应。
 *
 * @param name     模板名
 * @param version  实际使用的版本
 * @param messages 渲染后的消息
 */
public record PromptRenderResDTO(String name, int version, List<PromptMessage> messages) {

    /**
     * 防御性复制列表。
     */
    public PromptRenderResDTO {
        messages = Objects.isNull(messages) ? List.of() : List.copyOf(messages);
    }
}
