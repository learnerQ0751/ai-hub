package com.qtua.aihub.manager.catalog;

import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据库模型目录（主实现，REQ-4.1）。
 *
 * <p>别名/部署配置已从 YAML 迁到 DB；启动时若无数据由 {@link ModelCatalogSeeder}
 * 从 YAML 灌入。YAML 实现保留用于迁移来源与单测。</p>
 */
@Component
@Primary
public class DbModelCatalog implements ModelCatalog {

    private final ModelCatalogManager catalogManager;

    /**
     * 构造目录。
     *
     * @param catalogManager 目录数据门面
     */
    public DbModelCatalog(ModelCatalogManager catalogManager) {
        this.catalogManager = catalogManager;
    }

    @Override
    public List<ModelDeployment> findDeployments(String alias) {
        return catalogManager.deploymentsByAlias().getOrDefault(alias, List.of());
    }

    @Override
    public Set<String> listAliases() {
        return catalogManager.aliasNames();
    }

    @Override
    public List<String> findFallbacks(String alias) {
        Map<String, List<String>> fallbacks = catalogManager.fallbacksByAlias();
        return fallbacks.getOrDefault(alias, List.of());
    }
}
