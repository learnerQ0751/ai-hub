package com.qtua.aihub.common.resilience;

import com.qtua.aihub.common.catalog.ModelDeployment;

/**
 * 部署健康状态端口。
 *
 * <p>容错编排（core）只依赖本端口判断部署是否冷却、记录成功/失败；
 * 实现必须把状态外置（Redis），保证多实例一致。Redis 不可用时按 fail-open
 * 处理：宁可尝试也不因健康状态存储故障导致整体不可用。</p>
 */
public interface DeploymentHealthStore {

    /**
     * 判断部署是否处于冷却期。
     *
     * @param deployment 部署
     * @return true 表示冷却中，不应被选择
     */
    boolean isCoolingDown(ModelDeployment deployment);

    /**
     * 记录一次可重试失败；达到阈值后进入冷却。
     *
     * @param deployment 失败部署
     */
    void recordFailure(ModelDeployment deployment);

    /**
     * 记录一次成功；清除连续失败计数。
     *
     * @param deployment 成功部署
     */
    void recordSuccess(ModelDeployment deployment);
}
