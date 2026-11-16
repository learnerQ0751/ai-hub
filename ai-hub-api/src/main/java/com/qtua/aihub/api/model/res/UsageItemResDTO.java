package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 用量聚合明细行（REQ-4.3）。
 *
 * @param date             日期（yyyy-MM-dd）
 * @param tenantId         租户
 * @param projectId        项目
 * @param modelAlias       模型别名
 * @param keyId            Key 哈希（已掩码）
 * @param requests         请求数
 * @param promptTokens     输入 Token
 * @param completionTokens 输出 Token
 * @param totalTokens      总 Token
 * @param cost             成本
 */
public record UsageItemResDTO(@JsonProperty("date") String date,
                              @JsonProperty("tenant_id") String tenantId,
                              @JsonProperty("project_id") String projectId,
                              @JsonProperty("model_alias") String modelAlias,
                              @JsonProperty("key_id") String keyId,
                              long requests,
                              @JsonProperty("prompt_tokens") long promptTokens,
                              @JsonProperty("completion_tokens") long completionTokens,
                              @JsonProperty("total_tokens") long totalTokens,
                              BigDecimal cost) {
}
