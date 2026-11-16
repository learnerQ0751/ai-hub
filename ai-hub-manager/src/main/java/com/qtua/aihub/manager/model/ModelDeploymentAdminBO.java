package com.qtua.aihub.manager.model;

/**
 * 管理端部署实例视图（不含凭证）。
 *
 * @param deploymentKey 部署键
 * @param apiBase       上游地址
 * @param upstreamModel 上游真实模型
 * @param weight        权重
 * @param priority      优先级
 * @param status        状态
 */
public record ModelDeploymentAdminBO(String deploymentKey, String apiBase, String upstreamModel,
                                     int weight, int priority, String status) {
}
