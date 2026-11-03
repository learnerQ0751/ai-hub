package com.qtua.aihub.api.model.res;

import java.util.List;
import java.util.Objects;

/**
 * Prompt 版本历史响应。
 *
 * @param versions 版本列表（倒序）
 */
public record PromptVersionListResDTO(List<PromptResDTO> versions) {

    /**
     * 防御性复制列表。
     */
    public PromptVersionListResDTO {
        versions = Objects.isNull(versions) ? List.of() : List.copyOf(versions);
    }
}
