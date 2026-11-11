package com.qtua.aihub.api.model.res;

import java.util.List;
import java.util.Objects;

/**
 * 召回记忆响应。
 *
 * @param memories 命中记忆（创建时间倒序）；无命中为空列表
 * @param count    命中条数
 */
public record MemoryRecallResDTO(List<MemoryResDTO> memories, int count) {

    /**
     * 防御性复制列表；无命中返回空列表。
     */
    public MemoryRecallResDTO {
        memories = Objects.isNull(memories) ? List.of() : List.copyOf(memories);
    }
}
