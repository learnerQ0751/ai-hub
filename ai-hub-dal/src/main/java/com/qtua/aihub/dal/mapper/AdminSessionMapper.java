package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.AdminSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * `admin_sessions` 表 Mapper。
 */
@Mapper
public interface AdminSessionMapper {

    /**
     * 插入会话。
     *
     * @param session 会话记录
     */
    void insert(AdminSessionDO session);

    /**
     * 按令牌哈希查询会话。
     *
     * @param tokenHash SHA-256 摘要
     * @return 会话记录；不存在时为空
     */
    Optional<AdminSessionDO> selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 删除会话。
     *
     * @param tokenHash SHA-256 摘要
     * @return 受影响行数
     */
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 清理已过期会话。
     *
     * @param now 当前时间
     * @return 删除行数
     */
    int deleteExpired(@Param("now") Instant now);
}
