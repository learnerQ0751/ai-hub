package com.qtua.aihub.common.pricing;

import java.util.Optional;

/**
 * 模型价格目录端口。
 *
 * <p>按上游真实模型名查询价格；当前实现为 YAML 配置
 * （{@code YamlPricingCatalog}），未来可切换数据库或远程价格服务。
 * 业务层不直接依赖配置类。</p>
 */
public interface PricingCatalog {

    /**
     * 查询模型单价。
     *
     * @param upstreamModel 上游真实模型名
     * @return 单价；未配置时为空（调用方按 0 成本处理并告警）
     */
    Optional<ModelPrice> findPrice(String upstreamModel);
}
