package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.CallLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * `call_logs` 表 Mapper。
 *
 * <p>写入由 manager 层异步调用；按 request_id 查询用于排障与后续管理端（REQ-4.4）。</p>
 */
@Mapper
public interface CallLogMapper {

    /**
     * 插入一条调用日志。
     *
     * @param callLog 数据对象
     */
    void insert(CallLogDO callLog);

    /**
     * 按请求 ID 查询最近一条日志。
     *
     * @param requestId 请求追踪 ID
     * @return 日志；不存在时为空
     */
    Optional<CallLogDO> selectByRequestId(@Param("requestId") String requestId);

    /**
     * 统计某个虚拟 Key 的调用次数（供测试与后续成本统计使用）。
     *
     * @param keyId 虚拟 Key 的 SHA-256
     * @return 调用次数
     */
    int countByKeyId(@Param("keyId") String keyId);

    /**
     * 查询某个虚拟 Key 最近的调用日志。
     *
     * @param keyId 虚拟 Key 的 SHA-256
     * @param limit 返回条数
     * @return 按发生时间倒序的日志
     */
    List<CallLogDO> selectRecentByKeyId(@Param("keyId") String keyId,
                                        @Param("limit") int limit);

    /**
     * 管理端日志分页查询。
     *
     * @param tenantId  租户（可空）
     * @param projectId 项目（可空）
     * @param model     模型别名（可空）
     * @param keyId     Key 哈希（可空）
     * @param status    HTTP 状态（可空）
     * @param from      起始时间（可空）
     * @param to        结束时间（可空）
     * @param limit     每页条数
     * @param offset    偏移
     * @return 日志列表
     */
    List<CallLogDO> selectPage(@Param("tenantId") String tenantId,
                               @Param("projectId") String projectId,
                               @Param("model") String model,
                               @Param("keyId") String keyId,
                               @Param("status") Integer status,
                               @Param("from") java.time.Instant from,
                               @Param("to") java.time.Instant to,
                               @Param("limit") int limit,
                               @Param("offset") int offset);
}
