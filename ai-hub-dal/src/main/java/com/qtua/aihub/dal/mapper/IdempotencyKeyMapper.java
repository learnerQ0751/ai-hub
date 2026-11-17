package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.IdempotencyKeyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 幂等键数据访问（REQ-5.2）。
 */
@Mapper
public interface IdempotencyKeyMapper {

    /**
     * 预占幂等键（唯一约束兜底并发）。
     *
     * @param key         幂等键
     * @param requestHash 请求指纹
     * @return 受影响行数
     */
    int insert(@Param("key") String key, @Param("requestHash") String requestHash);

    /**
     * 按幂等键查询。
     *
     * @param key 幂等键
     * @return 记录
     */
    Optional<IdempotencyKeyDO> selectByKey(@Param("key") String key);

    /**
     * 回填首次响应。
     *
     * @param key         幂等键
     * @param status      状态码
     * @param contentType 响应类型
     * @param body        响应体
     * @return 受影响行数
     */
    int updateResponse(@Param("key") String key, @Param("status") int status,
                       @Param("contentType") String contentType, @Param("body") String body);

    /**
     * 释放幂等键（业务失败时允许重试）。
     *
     * @param key 幂等键
     * @return 受影响行数
     */
    int deleteByKey(@Param("key") String key);
}
