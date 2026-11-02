package com.qtua.aihub.core.routing;

import com.qtua.aihub.common.catalog.ModelDeployment;

import java.util.List;

/**
 * 部署选择策略。
 *
 * <p>实现类必须无状态或线程安全；多实例部署时不得依赖本机内存中的轮询状态。</p>
 */
public interface DeploymentSelector {

    /**
     * 从同一别名的部署列表中选择一个部署。
     *
     * @param alias       模型别名（用于日志/未来策略）
     * @param deployments 部署列表（非空）
     * @return 选中的部署
     */
    ModelDeployment select(String alias, List<ModelDeployment> deployments);
}
