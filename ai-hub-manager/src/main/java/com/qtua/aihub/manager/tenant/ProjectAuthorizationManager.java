package com.qtua.aihub.manager.tenant;

import com.qtua.aihub.common.tenant.ProjectAuthorizationPort;
import com.qtua.aihub.dal.mapper.ProjectAuthorizationMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目授权数据门面（REQ-4.1）。
 *
 * <p>Key 鉴权时加载授权项目写入认证上下文；授权为追加式，同租户内生效。</p>
 */
@Component
public class ProjectAuthorizationManager implements ProjectAuthorizationPort {

    private final ProjectAuthorizationMapper authorizationMapper;

    /**
     * 构造门面。
     *
     * @param authorizationMapper 授权 Mapper
     */
    public ProjectAuthorizationManager(ProjectAuthorizationMapper authorizationMapper) {
        this.authorizationMapper = authorizationMapper;
    }

    /**
     * 查询源项目被授权访问的目标项目。
     *
     * @param tenantId        租户
     * @param sourceProjectId 源项目
     * @return 目标项目列表
     */
    @Override
    public List<String> findAuthorizedProjects(String tenantId, String sourceProjectId) {
        return authorizationMapper.selectTargets(tenantId, sourceProjectId);
    }

    /**
     * 新增授权（幂等）。
     *
     * @param tenantId        租户
     * @param sourceProjectId 源项目
     * @param targetProjectId 目标项目
     * @param resourceType    资源类型
     */
    public void authorize(String tenantId, String sourceProjectId, String targetProjectId,
                          String resourceType) {
        authorizationMapper.insert(tenantId, sourceProjectId, targetProjectId, resourceType);
    }
}
