package com.qtua.aihub.common.memory;

import java.util.List;

/**
 * 记忆召回端口。
 *
 * <p>本期仅提供结构化（关键词 + 时间范围 + 作用域）实现；后续接入向量检索时
 * 新增实现并在此端口下切换，Service 不感知存储细节。</p>
 */
public interface MemoryRecallPort {

    /**
     * 按作用域与过滤条件召回未过期记忆。
     *
     * @param query 召回条件
     * @return 命中记忆（按创建时间倒序）；无命中返回空列表
     */
    List<MemoryEntry> recall(MemoryQuery query);
}
