package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 项目级授权（REQ-4.1/4.4）。
 *
 * @param tenantId        租户
 * @param sourceProjectId 源项目（Key 绑定项目）
 * @param targetProjectId 目标项目
 * @param resourceType    MEMORY/KNOWLEDGE/ALL
 */
public record ProjectAuthorizationReqDTO(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("source_project_id") String sourceProjectId,
        @JsonProperty("target_project_id") String targetProjectId,
        @JsonProperty("resource_type") String resourceType) {
}
