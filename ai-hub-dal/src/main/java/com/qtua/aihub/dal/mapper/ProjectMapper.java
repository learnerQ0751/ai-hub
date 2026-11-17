package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.ProjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目列表 Mapper（§7.1）。
 *
 * <p>项目表只登记显式建过的项目；实际使用中的租户/项目对可能先出现在
 * api_keys 或 rag_documents 中，列表把三处合并，避免管理台选不到项目。</p>
 */
@Mapper
public interface ProjectMapper {

    /**
     * 合并项目表与 Key/文档中出现过的租户+项目对，按租户、项目升序分页。
     *
     * @param tenantId 租户过滤（可空）
     * @param status   生效状态过滤 ACTIVE/DISABLED（可空）
     * @param limit    每页条数
     * @param offset   偏移
     * @return 项目列表
     */
    List<ProjectDO> selectPage(@Param("tenantId") String tenantId,
                               @Param("status") String status,
                               @Param("limit") int limit,
                               @Param("offset") int offset);
}
