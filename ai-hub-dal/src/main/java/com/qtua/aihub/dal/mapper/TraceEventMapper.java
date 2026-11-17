package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.TraceEventDO;
import com.qtua.aihub.dal.model.TraceSummaryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * `trace_events` 表 Mapper（REQ-4.2）。
 */
@Mapper
public interface TraceEventMapper {

    /**
     * 追加一条分段事件。
     *
     * @param event 事件
     */
    void insert(TraceEventDO event);

    /**
     * 按 trace_id 查询事件（时间顺序）。
     *
     * @param traceId 追踪 ID
     * @return 事件列表
     */
    List<TraceEventDO> selectByTraceId(@Param("traceId") String traceId);

    /**
     * Trace 列表：按 trace_id 聚合并按首个事件时间倒序分页（§7.1）。
     *
     * @param tenantId  租户（可空）
     * @param projectId 项目（可空）
     * @param keyId     Key 哈希（可空）
     * @param status    聚合后最大状态码（可空）
     * @param from      起始时间（可空）
     * @param to        结束时间（可空）
     * @param limit     每页条数
     * @param offset    偏移
     * @return 聚合行
     */
    List<TraceSummaryDO> selectTracePage(@Param("tenantId") String tenantId,
                                         @Param("projectId") String projectId,
                                         @Param("keyId") String keyId,
                                         @Param("status") Integer status,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);
}
