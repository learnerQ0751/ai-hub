package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 清理记忆请求。
 *
 * <p>提供 session_id 时只删除该会话；为空时删除该用户在当前项目的全部记忆。
 * 物理删除并写审计，审计原因可选。</p>
 *
 * @param tenantId  租户 ID
 * @param projectId 项目 ID
 * @param userId    用户 ID
 * @param sessionId 会话 ID（可选）
 * @param reason    删除原因（可选）
 */
public record MemoryClearReqDTO(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("session_id") String sessionId,
        String reason) {
}
