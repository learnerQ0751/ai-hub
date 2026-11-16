package com.qtua.aihub.common.audit;

import java.util.Map;

/**
 * 审计记录端口（REQ-4.5）。
 *
 * <p>业务只描述「谁对什么做了什么」；actor 由实现从租户上下文派生（master/Key 哈希），
 * before/after 必须是脱敏快照，禁止写入明文密钥。</p>
 */
public interface AuditRecorder {

    /** 空实现（单测/未启用）。 */
    AuditRecorder NOOP = (tenantId, action, target, before, after) -> {
    };

    /**
     * 记录一条审计。
     *
     * @param tenantId 租户
     * @param action   动作（KEY_BLOCK / PROMPT_PUBLISH 等）
     * @param target   目标（Key 哈希 / Prompt 名 / Server 名 / 别名等）
     * @param before   变更前快照（可空）
     * @param after    变更后快照（可空）
     */
    void record(String tenantId, String action, String target, Map<String, Object> before,
                Map<String, Object> after);
}
