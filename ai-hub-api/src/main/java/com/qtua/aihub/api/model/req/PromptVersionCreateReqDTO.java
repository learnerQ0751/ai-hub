package com.qtua.aihub.api.model.req;

import java.util.List;
import java.util.Objects;

/**
 * 追加 Prompt 新版本请求。
 *
 * @param content   模板内容（含 {{var}}）
 * @param variables 变量声明
 */
public record PromptVersionCreateReqDTO(String content, List<String> variables) {

    /**
     * 防御性复制变量列表。
     */
    public PromptVersionCreateReqDTO {
        variables = Objects.isNull(variables) ? List.of() : List.copyOf(variables);
    }
}
