package com.qtua.aihub.common.tenant;

import java.util.List;

/**
 * 项目级授权查询端口（REQ-4.1）。
 *
 * <p>接口放在 common，入口过滤器只依赖端口；实现由 manager 提供，
 * 避免入口层直接依赖数据访问层。</p>
 */
public interface ProjectAuthorizationPort {

    /**
     * 查询源项目被显式授权可访问的目标项目。
     *
     * @param tenantId        租户
     * @param sourceProjectId 源项目（Key 绑定项目）
     * @return 目标项目列表；无授权返回空列表
     */
    List<String> findAuthorizedProjects(String tenantId, String sourceProjectId);
}
