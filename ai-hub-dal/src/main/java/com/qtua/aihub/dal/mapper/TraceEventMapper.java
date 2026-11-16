package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.TraceEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
