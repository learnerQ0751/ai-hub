package com.qtua.aihub.common.catalog;

import java.util.List;
import java.util.Set;

/**
 * 模型目录端口。
 *
 * <p>业务层只依赖本接口读取"别名 → 部署"；当前实现为 YAML 配置
 * （{@code YamlModelCatalog}），未来切换数据库实现时业务代码不变。
 * 未知别名返回空列表，不返回 null。</p>
 */
public interface ModelCatalog {

    /**
     * 查询别名下的部署列表。
     *
     * @param alias 模型别名
     * @return 部署列表（不可变）；未知别名返回空列表
     */
    List<ModelDeployment> findDeployments(String alias);

    /**
     * 列出全部模型别名，保持配置/数据源顺序。
     *
     * @return 别名集合（不可变、有序）
     */
    Set<String> listAliases();

    /**
     * 查询别名的 fallback 降级链。
     *
     * @param alias 模型别名
     * @return 备用别名列表（不可变、有序）；未配置返回空列表
     */
    List<String> findFallbacks(String alias);
}
