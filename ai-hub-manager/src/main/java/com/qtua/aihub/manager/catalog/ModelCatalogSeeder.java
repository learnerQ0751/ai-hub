package com.qtua.aihub.manager.catalog;

import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.dal.mapper.ModelCatalogMapper;
import com.qtua.aihub.dal.model.ModelAliasDO;
import com.qtua.aihub.dal.model.ModelDeploymentDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 模型目录启动种子：DB 为空时把 YAML 配置迁入数据库（REQ-4.1）。
 *
 * <p>只灌入一次；之后以数据库为准，管理 API 修改即时生效。多实例同时启动时
 * 由别名唯一约束兜底，冲突实例跳过。</p>
 */
@Component
public class ModelCatalogSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ModelCatalogSeeder.class);
    private static final int DEFAULT_WEIGHT = 1;

    private final ModelCatalogManager catalogManager;
    private final GatewayProperties properties;
    private final boolean refresh;

    /**
     * 构造种子。
     *
     * @param catalogManager 目录数据门面
     * @param properties     网关 YAML 配置
     * @param seedMode       init（仅空库灌入）/ refresh（每次启动按 YAML 覆盖，测试用）
     */
    public ModelCatalogSeeder(ModelCatalogManager catalogManager, GatewayProperties properties,
                              @org.springframework.beans.factory.annotation.Value(
                                      "${gateway.catalog-seed-mode:init}") String seedMode) {
        this.catalogManager = catalogManager;
        this.properties = properties;
        this.refresh = "refresh".equalsIgnoreCase(seedMode);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (ObjectUtils.isEmpty(properties.getModelList())) {
            return;
        }
        if (!refresh && catalogManager.hasAliases()) {
            return;
        }
        ModelCatalogMapper mapper = catalogManager.mapper();
        Instant now = Instant.now();
        try {
            for (Map.Entry<String, List<GatewayProperties.Deployment>> entry
                    : properties.getModelList().entrySet()) {
                seedAlias(mapper, entry.getKey(), entry.getValue(), now);
            }
            LOG.info("model catalog seeded from YAML: aliases={}", properties.getModelList().size());
        } catch (RuntimeException ex) {
            // 并发启动时另一实例已灌入：以数据库结果为准，不阻塞启动。
            LOG.warn("model catalog seed skipped: {}", ex.getClass().getSimpleName());
        }
    }

    private void seedAlias(ModelCatalogMapper mapper, String alias,
                           List<GatewayProperties.Deployment> deployments, Instant now) {
        ModelAliasDO aliasDO = mapper.selectByAlias(alias).orElseGet(
                () -> insertAlias(mapper, alias, now));
        if (refresh && !ObjectUtils.isEmpty(aliasDO.getId())) {
            aliasDO.setFallbacks(catalogManager.writeFallbacks(
                    properties.getFallbacks().getOrDefault(alias, List.of())));
            aliasDO.setStatus("ACTIVE");
            aliasDO.setUpdatedAt(now);
            mapper.updateAlias(aliasDO);
        }
        for (int index = 0; index < deployments.size(); index++) {
            GatewayProperties.Deployment deployment = deployments.get(index);
            String deploymentKey = alias + "#" + index;
            ModelDeploymentDO deploymentDO = mapper.selectDeploymentByKey(deploymentKey)
                    .orElseGet(() -> insertDeployment(mapper, deploymentKey, deployment, now));
            if (refresh) {
                deploymentDO.setApiBase(deployment.getApiBase());
                deploymentDO.setApiKeyCipher(
                        catalogManager.cipher().encrypt(deployment.getApiKey()));
                deploymentDO.setUpstreamModel(deployment.getModel());
                deploymentDO.setStatus("ACTIVE");
                deploymentDO.setUpdatedAt(now);
                mapper.updateDeployment(deploymentDO);
            }
            if (mapper.updateAliasDeployment(aliasDO.getId(), deploymentDO.getId(),
                    weightOf(deployment), priorityOf(deployment)) == 0) {
                mapper.insertAliasDeployment(aliasDO.getId(), deploymentDO.getId(),
                        weightOf(deployment), priorityOf(deployment));
            }
        }
    }

    private ModelAliasDO insertAlias(ModelCatalogMapper mapper, String alias, Instant now) {
        ModelAliasDO aliasDO = new ModelAliasDO();
        aliasDO.setAlias(alias);
        aliasDO.setFallbacks(catalogManager.writeFallbacks(
                properties.getFallbacks().getOrDefault(alias, List.of())));
        aliasDO.setStatus("ACTIVE");
        aliasDO.setCreatedAt(now);
        aliasDO.setUpdatedAt(now);
        mapper.insertAlias(aliasDO);
        return aliasDO;
    }

    private ModelDeploymentDO insertDeployment(ModelCatalogMapper mapper, String deploymentKey,
                                               GatewayProperties.Deployment deployment,
                                               Instant now) {
        ModelDeploymentDO deploymentDO = new ModelDeploymentDO();
        deploymentDO.setDeploymentKey(deploymentKey);
        deploymentDO.setApiBase(deployment.getApiBase());
        deploymentDO.setApiKeyCipher(catalogManager.cipher().encrypt(deployment.getApiKey()));
        deploymentDO.setUpstreamModel(deployment.getModel());
        deploymentDO.setStatus("ACTIVE");
        deploymentDO.setCreatedAt(now);
        deploymentDO.setUpdatedAt(now);
        mapper.insertDeployment(deploymentDO);
        return deploymentDO;
    }

    private int weightOf(GatewayProperties.Deployment deployment) {
        if (!ObjectUtils.isEmpty(deployment.getRpm()) && deployment.getRpm() > 0) {
            return deployment.getRpm();
        }
        if (!ObjectUtils.isEmpty(deployment.getTpm()) && deployment.getTpm() > 0) {
            return deployment.getTpm();
        }
        return DEFAULT_WEIGHT;
    }

    private int priorityOf(GatewayProperties.Deployment deployment) {
        return !ObjectUtils.isEmpty(deployment.getPriority()) && deployment.getPriority() >= 0
                ? deployment.getPriority() : 0;
    }
}
