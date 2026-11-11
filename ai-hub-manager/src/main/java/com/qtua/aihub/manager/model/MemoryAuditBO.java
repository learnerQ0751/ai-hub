package com.qtua.aihub.manager.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 记忆物理删除审计业务对象。
 *
 * @param auditId      审计 ID
 * @param tenantId     租户 ID
 * @param projectId    项目 ID
 * @param userId       用户 ID
 * @param sessionId    会话 ID；null 表示用户级全量
 * @param operation    操作类型（CLEAR）
 * @param deletedCount 实际删除条数
 * @param memoryIds    被删除的记忆 ID（最多上限条）
 * @param operatorKey  执行删除的虚拟 Key 哈希；master 为 null
 * @param reason       删除原因
 * @param createdAt    发生时间
 */
public record MemoryAuditBO(String auditId, String tenantId, String projectId, String userId,
                            String sessionId, String operation, int deletedCount,
                            List<String> memoryIds, String operatorKey, String reason,
                            Instant createdAt) {

    /**
     * 防御性复制删除 ID 列表。
     */
    public MemoryAuditBO {
        memoryIds = Objects.isNull(memoryIds) ? List.of() : List.copyOf(memoryIds);
    }
}
