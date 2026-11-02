package com.qtua.aihub.manager.catalog;

import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.GatewayProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * YAML 模型目录实现。
 *
 * <p>启动时一次性把配置映射为不可变目录；请求链路只读内存，无状态、多实例安全。
 * 权重规则与原实现一致：rpm 优先 → tpm 回退 → 默认 1 → 非正数回退 1。
 * 配置合法性（同源/混用）由 {@link GatewayProperties} 在启动时校验。</p>
 */
@Component
public class YamlModelCatalog implements ModelCatalog {

    private static final int DEFAULT_WEIGHT = 1;
    private static final int DEFAULT_PRIORITY = 0;

    private final Map<String, List<ModelDeployment>> deploymentsByAlias;
    private final Map<String, List<String>> fallbacksByAlias;
    private final Set<String> aliases;

    /**
     * 从 YAML 配置构建只读目录。
     *
     * @param properties 网关配置
     */
    public YamlModelCatalog(GatewayProperties properties) {
        Map<String, List<ModelDeployment>> mapped = new LinkedHashMap<>();
        properties.getModelList().forEach((alias, deployments) ->
                mapped.put(alias, deployments.stream()
                        .map(deployment -> new ModelDeployment(
                                alias,
                                deployment.getApiBase(),
                                deployment.getApiKey(),
                                deployment.getModel(),
                                weightOf(deployment),
                                priorityOf(deployment)))
                        .toList()));
        Map<String, List<String>> fallbacks = new LinkedHashMap<>();
        properties.getFallbacks().forEach((alias, targets) ->
                fallbacks.put(alias, List.copyOf(targets)));
        this.deploymentsByAlias = Collections.unmodifiableMap(mapped);
        this.fallbacksByAlias = Collections.unmodifiableMap(fallbacks);
        this.aliases = Collections.unmodifiableSet(new LinkedHashSet<>(mapped.keySet()));
    }

    /**
     * 查询别名下的部署列表。
     *
     * @param alias 模型别名
     * @return 不可变部署列表；未知别名返回空列表
     */
    @Override
    public List<ModelDeployment> findDeployments(String alias) {
        return deploymentsByAlias.getOrDefault(alias, List.of());
    }

    /**
     * 列出全部别名，保持 YAML 顺序。
     *
     * @return 不可变别名集合
     */
    @Override
    public Set<String> listAliases() {
        return aliases;
    }

    /**
     * 查询别名的 fallback 降级链。
     *
     * @param alias 模型别名
     * @return 不可变备用别名列表；未配置返回空列表
     */
    @Override
    public List<String> findFallbacks(String alias) {
        return fallbacksByAlias.getOrDefault(alias, List.of());
    }

    /**
     * 权重规则：rpm 优先，其次 tpm，都没有则为 1。
     *
     * @param deployment 配置中的部署
     * @return 有效权重
     */
    private static int weightOf(GatewayProperties.Deployment deployment) {
        if (!ObjectUtils.isEmpty(deployment.getRpm())) {
            return validWeight(deployment.getRpm());
        }
        if (!ObjectUtils.isEmpty(deployment.getTpm())) {
            return validWeight(deployment.getTpm());
        }
        return DEFAULT_WEIGHT;
    }

    private static int validWeight(Integer weight) {
        return !ObjectUtils.isEmpty(weight) && weight > 0 ? weight : DEFAULT_WEIGHT;
    }

    /**
     * 主备优先级：未配置或负数按 0（最高优先级）处理。
     *
     * @param deployment 配置中的部署
     * @return 非负优先级
     */
    private static int priorityOf(GatewayProperties.Deployment deployment) {
        return !ObjectUtils.isEmpty(deployment.getPriority()) && deployment.getPriority() >= 0
                ? deployment.getPriority() : DEFAULT_PRIORITY;
    }
}
