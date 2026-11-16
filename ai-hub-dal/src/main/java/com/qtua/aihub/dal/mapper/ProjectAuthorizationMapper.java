package com.qtua.aihub.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * `project_authorizations` 表 Mapper（REQ-4.1）。
 *
 * <p>只做追加与查询；授权撤销由管理端后续提供，本期不物理删除历史授权。</p>
 */
@Mapper
public interface ProjectAuthorizationMapper {

    /**
     * 插入授权（重复授权幂等）。
     *
     * @param tenantId        租户
     * @param sourceProjectId 源项目
     * @param targetProjectId 目标项目
     * @param resourceType    资源类型：MEMORY/KNOWLEDGE/ALL
     * @return 受影响行数
     */
    int insert(@Param("tenantId") String tenantId,
               @Param("sourceProjectId") String sourceProjectId,
               @Param("targetProjectId") String targetProjectId,
               @Param("resourceType") String resourceType);

    /**
     * 查询源项目被授权访问的目标项目。
     *
     * @param tenantId        租户
     * @param sourceProjectId 源项目
     * @return 目标项目列表
     */
    List<String> selectTargets(@Param("tenantId") String tenantId,
                               @Param("sourceProjectId") String sourceProjectId);
}
