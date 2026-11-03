package com.qtua.aihub.integration.prompt;

import com.qtua.aihub.common.prompt.PromptMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板文本 → 消息列表解析器。
 *
 * <p>约定（对齐 LiteLLM dotprompt 风格）：以 {@code System:} / {@code User:} /
 * {@code Assistant:} 开头的行开始一条新消息；其余行追加到当前消息；无前缀的
 * 首部内容默认视为 user 消息。</p>
 */
@Component
public class PromptMessageParser {

    private static final Pattern ROLE_LINE = Pattern.compile(
            "^(?i)(system|user|assistant):\\s?(.*)$");

    /**
     * 解析模板文本。
     *
     * @param content 渲染后的模板文本
     * @return 消息列表（至少一条）
     */
    public List<PromptMessage> parse(String content) {
        List<PromptMessage> messages = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            messages.add(new PromptMessage("user", ""));
            return messages;
        }
        String role = null;
        StringBuilder buffer = new StringBuilder();
        for (String line : content.split("\\r?\\n", -1)) {
            Matcher matcher = ROLE_LINE.matcher(line);
            if (matcher.matches()) {
                flush(messages, role, buffer);
                role = matcher.group(1).toLowerCase();
                buffer.append(matcher.group(2));
            } else if (StringUtils.hasText(role)) {
                buffer.append('\n').append(line);
            } else {
                // 首部无角色：默认 user。
                role = "user";
                buffer.append(line);
            }
        }
        flush(messages, role, buffer);
        if (messages.isEmpty()) {
            messages.add(new PromptMessage("user", content));
        }
        return messages;
    }

    private void flush(List<PromptMessage> messages, String role, StringBuilder buffer) {
        if (StringUtils.hasText(role)) {
            messages.add(new PromptMessage(role, buffer.toString().stripTrailing()));
        }
        buffer.setLength(0);
    }
}
