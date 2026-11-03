package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt 对话请求（渲染 → chat 链路）。
 *
 * @param version     模板版本（null 表示最新发布版）
 * @param variables   变量取值
 * @param model       模型别名（必填）
 * @param temperature 采样温度（可选）
 * @param maxTokens   最大输出 token（可选）
 */
public record PromptChatReqDTO(
        Integer version,
        Map<String, String> variables,
        String model,
        BigDecimal temperature,
        @JsonProperty("max_tokens") Integer maxTokens) {

    /**
     * 防御性复制变量。
     */
    public PromptChatReqDTO {
        variables = Objects.isNull(variables) ? Map.of() : Map.copyOf(variables);
    }
}
