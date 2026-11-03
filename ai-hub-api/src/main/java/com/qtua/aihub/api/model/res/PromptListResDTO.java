package com.qtua.aihub.api.model.res;

import java.util.List;
import java.util.Objects;

/**
 * Prompt 模板列表响应（每个模板的最新发布版本）。
 *
 * @param prompts 模板列表
 */
public record PromptListResDTO(List<PromptResDTO> prompts) {

    /**
     * 防御性复制列表。
     */
    public PromptListResDTO {
        prompts = Objects.isNull(prompts) ? List.of() : List.copyOf(prompts);
    }
}
