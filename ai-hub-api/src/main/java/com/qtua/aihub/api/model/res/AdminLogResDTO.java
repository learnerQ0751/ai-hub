package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 管理端调用日志视图（Key 哈希已掩码）。
 */
public record AdminLogResDTO(@JsonProperty("request_id") String requestId,
                             @JsonProperty("trace_id") String traceId,
                             @JsonProperty("key_id") String keyId,
                             @JsonProperty("tenant_id") String tenantId,
                             @JsonProperty("project_id") String projectId,
                             @JsonProperty("model_alias") String modelAlias,
                             @JsonProperty("deployment_model") String deploymentModel,
                             @JsonProperty("prompt_tokens") int promptTokens,
                             @JsonProperty("completion_tokens") int completionTokens,
                             @JsonProperty("total_tokens") int totalTokens,
                             BigDecimal cost,
                             @JsonProperty("latency_ms") long latencyMs,
                             int status,
                             @JsonProperty("error_type") String errorType,
                             @JsonProperty("call_type") String callType,
                             @JsonProperty("target_name") String targetName,
                             @JsonProperty("created_at") String createdAt) {
}
