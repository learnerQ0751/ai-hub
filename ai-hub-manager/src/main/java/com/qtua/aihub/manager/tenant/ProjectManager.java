package com.qtua.aihub.manager.tenant;

import com.qtua.aihub.dal.mapper.ProjectMapper;
import com.qtua.aihub.dal.model.ProjectDO;
import com.qtua.aihub.manager.model.ProjectBO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目数据门面（§7.1 项目列表）。
 *
 * <p>只做 DO→BO 转换；合并与分页口径由 Mapper 承担。</p>
 */
@Component
public class ProjectManager {

    private final ProjectMapper projectMapper;

    /**
     * 构造门面。
     *
     * @param projectMapper 项目 Mapper
     */
    public ProjectManager(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    /**
     * 分页查询租户下的项目。
     *
     * @param tenantId 租户过滤（可空）
     * @param status   状态过滤（可空）
     * @param limit    每页条数
     * @param offset   偏移
     * @return 项目列表
     */
    public List<ProjectBO> list(String tenantId, String status, int limit, int offset) {
        return projectMapper.selectPage(tenantId, status, limit, offset).stream()
                .map(this::toBO).toList();
    }

    private ProjectBO toBO(ProjectDO dataObject) {
        return new ProjectBO(dataObject.getTenantId(), dataObject.getProjectId(),
                dataObject.getName(), dataObject.getStatus(), dataObject.getCreatedAt());
    }
}
