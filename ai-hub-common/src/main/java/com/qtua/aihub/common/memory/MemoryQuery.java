package com.qtua.aihub.common.memory;

import java.time.Instant;

/**
 * 记忆召回条件。
 *
 * <p>sessionId 为空表示召回用户级记忆；crossProject 仅在用户级记忆且配置开启时生效。
 * keyword 由服务层转义后传入（已带 % 通配），from/to 为用户时区外的绝对时间。</p>
 *
 * @param tenantId     租户 ID
 * @param projectId    项目 ID
 * @param userId       用户 ID
 * @param sessionId    会话 ID；null 表示用户级
 * @param keyword      关键词（已转义并包裹 %）；null 表示不过滤
 * @param from         创建时间下界；null 表示不限
 * @param to           创建时间上界；null 表示不限
 * @param limit        返回条数上限
 * @param crossProject 是否跨项目召回用户级记忆
 */
public record MemoryQuery(String tenantId, String projectId, String userId, String sessionId,
                          String keyword, Instant from, Instant to, int limit,
                          boolean crossProject) {
}
