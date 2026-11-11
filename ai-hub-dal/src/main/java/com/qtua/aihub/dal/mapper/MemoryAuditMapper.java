package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.MemoryAuditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * `memory_audit_logs` 表 Mapper（物理删除审计）。
 */
@Mapper
public interface MemoryAuditMapper {

    /**
     * 插入审计记录。
     *
     * @param audit 审计数据对象
     */
    void insert(MemoryAuditDO audit);

    /**
     * 按审计 ID 查询。
     *
     * @param auditId 审计 ID
     * @return 审计记录；不存在为空
     */
    Optional<MemoryAuditDO> selectByAuditId(@Param("auditId") String auditId);
}
