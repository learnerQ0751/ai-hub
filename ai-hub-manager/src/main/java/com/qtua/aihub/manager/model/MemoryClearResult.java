package com.qtua.aihub.manager.model;

/**
 * 记忆清理结果。
 *
 * @param deletedCount 实际物理删除条数
 * @param auditId      审计 ID；删除 0 条时为 null
 */
public record MemoryClearResult(int deletedCount, String auditId) {
}
