package com.qtua.aihub.api.model.req;

import java.util.Map;
import java.util.Objects;

/**
 * Prompt 渲染请求。
 *
 * @param version   版本（null 表示最新发布版）
 * @param variables 变量取值
 */
public record PromptRenderReqDTO(Integer version, Map<String, String> variables) {

    /**
     * 防御性复制变量。
     */
    public PromptRenderReqDTO {
        variables = Objects.isNull(variables) ? Map.of() : Map.copyOf(variables);
    }
}
