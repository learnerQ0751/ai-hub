package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.MemoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * `memories` 表 Mapper。
 *
 * <p>所有查询强制带 tenant_id + user_id；session_id 为空时只查用户级记忆。
 * 过期记录在 SQL 层过滤（惰性失效），不依赖定时任务。</p>
 */
@Mapper
public interface MemoryMapper {

    /**
     * 插入一条记忆。
     *
     * @param memory 数据对象
     */
    void insert(MemoryDO memory);

    /**
     * 按作用域召回未过期记忆。
     *
     * @param tenantId       租户 ID
     * @param projectId      项目 ID
     * @param userId         用户 ID
     * @param sessionId      会话 ID；null 表示用户级
     * @param keywordPattern 已转义的关键词模式（含 %）；null 表示不过滤
     * @param from           创建时间下界；null 表示不限
     * @param to             创建时间上界；null 表示不限
     * @param crossProject   用户级记忆是否跨项目
     * @param limit          返回条数上限
     * @return 记忆列表（创建时间倒序）
     */
    List<MemoryDO> selectForRecall(@Param("tenantId") String tenantId,
                                   @Param("projectId") String projectId,
                                   @Param("userId") String userId,
                                   @Param("sessionId") String sessionId,
                                   @Param("keywordPattern") String keywordPattern,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to,
                                   @Param("crossProject") boolean crossProject,
                                   @Param("limit") int limit);

    /**
     * 查询待删除的 memory_id（用于物理删除审计）。
     *
     * @param tenantId  租户 ID
     * @param projectId 项目 ID
     * @param userId    用户 ID
     * @param sessionId 会话 ID；null 表示该用户在当前项目的全部记忆
     * @param limit     最多记录条数
     * @return memory_id 列表
     */
    List<String> selectIdsForClear(@Param("tenantId") String tenantId,
                                   @Param("projectId") String projectId,
                                   @Param("userId") String userId,
                                   @Param("sessionId") String sessionId,
                                   @Param("limit") int limit);

    /**
     * 按作用域物理删除记忆。
     *
     * @param tenantId  租户 ID
     * @param projectId 项目 ID
     * @param userId    用户 ID
     * @param sessionId 会话 ID；null 表示删除该用户在当前项目的全部记忆
     * @return 删除条数
     */
    int deleteByScope(@Param("tenantId") String tenantId,
                      @Param("projectId") String projectId,
                      @Param("userId") String userId,
                      @Param("sessionId") String sessionId);
}
