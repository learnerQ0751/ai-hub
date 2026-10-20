package com.qtua.aihub.core.routing;

import com.qtua.aihub.config.GatewayProperties.Deployment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Random;

/**
 * simple-shuffle：无状态加权随机选择。
 *
 * <p>不保存任何轮询历史或成员变量 Map，因此多个实例各自随机也不会产生共享状态问题；
 * 代价是请求分布是概率性的，不保证严格交替。若未来需要严格平滑轮询，
 * 应把轮询状态放到 Redis，而不是恢复单机 Map。</p>
 */
@Component
public class WeightedRandomSelector implements DeploymentSelector {

    private static final int DEFAULT_WEIGHT = 1;

    /** 仅作为随机源；Random 本身线程安全，不属于业务状态。 */
    private final Random random;

    public WeightedRandomSelector() {
        this(new Random());
    }

    /**
     * 测试用构造器：注入固定种子的 Random，保证结果可复现。
     *
     * @param random 随机源
     */
    WeightedRandomSelector(Random random) {
        this.random = random;
    }

    /**
     * 按 rpm/tpm 权重随机选择一个部署。
     *
     * @param alias       模型别名（当前不参与权重计算，保留扩展点）
     * @param deployments 部署列表
     * @return 选中的部署
     */
    @Override
    public Deployment select(String alias, List<Deployment> deployments) {
        if (deployments.size() == 1) {
            return deployments.get(0);
        }
        long totalWeight = 0;
        for (Deployment deployment : deployments) {
            totalWeight += weightOf(deployment);
        }
        // 加权随机：票号落在哪个累计权重区间，就选择哪个部署。
        long ticket = random.nextLong(totalWeight);
        long cumulative = 0;
        for (Deployment deployment : deployments) {
            cumulative += weightOf(deployment);
            if (ticket < cumulative) {
                return deployment;
            }
        }
        return deployments.get(deployments.size() - 1);
    }

    /**
     * 权重规则：优先 rpm，其次 tpm，都没有则为 1；非正数回退 1。
     *
     * @param deployment 部署
     * @return 有效权重
     */
    private static long weightOf(Deployment deployment) {
        if (!ObjectUtils.isEmpty(deployment.getRpm())) {
            return validWeight(deployment.getRpm());
        }
        if (!ObjectUtils.isEmpty(deployment.getTpm())) {
            return validWeight(deployment.getTpm());
        }
        return DEFAULT_WEIGHT;
    }

    private static long validWeight(Integer weight) {
        return weight > 0 ? weight : DEFAULT_WEIGHT;
    }
}
