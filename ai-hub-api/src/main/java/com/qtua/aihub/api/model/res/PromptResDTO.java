package com.qtua.aihub.api.model.res;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Prompt 模板版本视图。
 *
 * @param name      模板名
 * @param version   版本号
 * @param status    状态（DRAFT/PUBLISHED/ARCHIVED）
 * @param content   模板内容
 * @param variables 变量声明
 * @param tenantId  租户 ID
 * @param createdBy 创建人
 * @param createdAt 创建时间
 */
public record PromptResDTO(
        String name,
        int version,
        String status,
        String content,
        List<String> variables,
        String tenantId,
        String createdBy,
        Instant createdAt) {

    /**
     * 防御性复制变量列表。
     */
    public PromptResDTO {
        variables = Objects.isNull(variables) ? List.of() : List.copyOf(variables);
    }
}
