package com.qtua.aihub.api.model.req;

import java.util.List;
import java.util.Objects;

/**
 * 创建 Prompt 模板请求（v1）。
 *
 * @param name      模板名（唯一，多版本）
 * @param content   模板内容（含 {{var}}）
 * @param variables 变量声明
 * @param tenantId  租户 ID（可选）
 * @param createdBy 创建人（可选）
 */
public record PromptCreateReqDTO(
        String name,
        String content,
        List<String> variables,
        String tenantId,
        String createdBy) {

    /**
     * 防御性复制变量列表。
     */
    public PromptCreateReqDTO {
        variables = Objects.isNull(variables) ? List.of() : List.copyOf(variables);
    }
}
