package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理端部署实例视图（api-key 永不回显）。
 *
 * @param deploymentKey 部署键
 * @param apiBase       上游地址
 * @param upstreamModel 上游真实模型
 * @param weight        权重
 * @param priority      优先级
 * @param status        状态
 */
public record AdminDeploymentResDTO(@JsonProperty("deployment_key") String deploymentKey,
                                    @JsonProperty("api_base") String apiBase,
                                    @JsonProperty("upstream_model") String upstreamModel,
                                    int weight, int priority, String status) {
}
