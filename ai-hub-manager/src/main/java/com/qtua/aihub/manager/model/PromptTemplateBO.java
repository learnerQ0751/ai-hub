package com.qtua.aihub.manager.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Prompt 模板业务对象。
 *
 * @param name      模板名
 * @param version   版本号
 * @param content   模板内容（含 {{var}}）
 * @param variables 变量声明
 * @param status    状态（DRAFT/PUBLISHED/ARCHIVED）
 * @param tenantId  租户 ID
 * @param createdBy 创建人
 * @param createdAt 创建时间
 */
public record PromptTemplateBO(
        String name,
        int version,
        String content,
        List<String> variables,
        String status,
        String tenantId,
        String createdBy,
        Instant createdAt) {

    /**
     * 防御性复制变量列表。
     */
    public PromptTemplateBO {
        variables = Objects.isNull(variables) ? List.of() : List.copyOf(variables);
    }
}
