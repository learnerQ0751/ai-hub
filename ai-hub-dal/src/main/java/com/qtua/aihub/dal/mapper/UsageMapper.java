package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.UsageAggregateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 用量聚合查询（REQ-4.3）。
 *
 * <p>数据来自 `v_usage_daily` 视图；日期边界为闭区间 [from, to]。</p>
 */
@Mapper
public interface UsageMapper {

    /**
     * 按维度查询用量。
     *
     * @param from       起始日期
     * @param to         结束日期
     * @param tenantId   租户（可空 = 全部，仅 master）
     * @param projectId  项目（可空 = 全部）
     * @param modelAlias 模型别名（可空）
     * @param keyId      Key 哈希（可空）
     * @return 聚合行
     */
    List<UsageAggregateDO> selectUsage(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("tenantId") String tenantId,
                                       @Param("projectId") String projectId,
                                       @Param("modelAlias") String modelAlias,
                                       @Param("keyId") String keyId);

    /**
     * 查询用到的上游模型（缺价告警）。
     *
     * @param from      起始日期
     * @param to        结束日期
     * @param tenantId  租户
     * @param projectId 项目
     * @return 上游模型列表
     */
    List<String> selectUsedUpstreamModels(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to,
                                          @Param("tenantId") String tenantId,
                                          @Param("projectId") String projectId);
}
