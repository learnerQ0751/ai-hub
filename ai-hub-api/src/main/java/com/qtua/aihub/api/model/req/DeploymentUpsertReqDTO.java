package com.qtua.aihub.api.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 模型部署实例新增/更新（REQ-4.4）。
 *
 * @param deploymentKey 部署键（可空，缺省为 alias#序）
 * @param apiBase       上游 OpenAI 兼容地址
 * @param apiKey        上游 Key（只写不回显）
 * @param upstreamModel 上游真实模型
 * @param weight        权重
 * @param priority      主备优先级
 * @param status        ACTIVE/DISABLED
 */
public record DeploymentUpsertReqDTO(
        @JsonProperty("deployment_key") String deploymentKey,
        @JsonProperty("api_base") String apiBase,
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("upstream_model") String upstreamModel,
        Integer weight,
        Integer priority,
        String status) {
}
