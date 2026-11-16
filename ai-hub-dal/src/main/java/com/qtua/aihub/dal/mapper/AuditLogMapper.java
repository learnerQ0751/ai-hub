package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * `audit_logs` 表 Mapper（REQ-4.5）。
 *
 * <p>只声明 insert 与查询：不提供 update/delete，应用层无法篡改审计。</p>
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 追加审计记录。
     *
     * @param audit 记录
     */
    void insert(AuditLogDO audit);

    /**
     * 分页查询审计。
     *
     * @param tenantId 租户（可空）
     * @param action   动作（可空）
     * @param target   目标（模糊，可空）
     * @param from     起始时间（可空）
     * @param to       结束时间（可空）
     * @param limit    每页条数
     * @param offset   偏移
     * @return 记录列表
     */
    List<AuditLogDO> selectPage(@Param("tenantId") String tenantId,
                                @Param("action") String action,
                                @Param("target") String target,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                @Param("limit") int limit,
                                @Param("offset") int offset);
}
