package com.qtua.aihub.common.prompt;

/**
 * 渲染后的单条消息。
 *
 * @param role    角色：system/user/assistant
 * @param content 内容
 */
public record PromptMessage(String role, String content) {
}
