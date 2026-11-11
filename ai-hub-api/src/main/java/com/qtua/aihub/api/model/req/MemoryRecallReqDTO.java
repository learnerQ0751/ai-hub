package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 召回记忆请求。
 *
 * <p>session_id 为空时召回用户级记忆；提供 session_id 时只召回该会话记忆。
 * cross_project 仅在用户级记忆且服务端开启共享时生效。</p>
 *
 * @param tenantId     租户 ID
 * @param projectId    项目 ID
 * @param userId       用户 ID
 * @param sessionId    会话 ID（可选）
 * @param keyword      关键词（可选）
 * @param from         创建时间下界（可选，ISO-8601）
 * @param to           创建时间上界（可选，ISO-8601）
 * @param limit        返回条数（可选）
 * @param crossProject 是否跨项目召回用户级记忆（可选）
 */
public record MemoryRecallReqDTO(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("session_id") String sessionId,
        String keyword,
        Instant from,
        Instant to,
        Integer limit,
        @JsonProperty("cross_project") Boolean crossProject) {
}
