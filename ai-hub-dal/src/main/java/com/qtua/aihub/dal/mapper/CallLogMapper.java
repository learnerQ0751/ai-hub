package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.CallLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
