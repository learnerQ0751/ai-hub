package com.qtua.aihub.common.memory;

/**
 * 记忆作用域：租户 + 项目 + 用户 + 可选会话。
 *
 * <p>所有读写必须携带完整作用域；sessionId 为空表示用户级记忆。</p>
 *
 * @param tenantId  租户 ID
 * @param projectId 项目 ID
 * @param userId    用户 ID
 * @param sessionId 会话 ID；用户级记忆为 null
 */
public record MemoryScope(String tenantId, String projectId, String userId, String sessionId) {
}
