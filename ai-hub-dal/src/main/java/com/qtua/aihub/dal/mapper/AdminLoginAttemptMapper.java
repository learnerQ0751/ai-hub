package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.AdminLoginAttemptDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * `admin_login_attempts` 表 Mapper。
 */
@Mapper
public interface AdminLoginAttemptMapper {

    /**
     * 按用户名查询失败计数。
     *
     * @param username 管理员账号
     * @return 计数记录；不存在时为空
     */
    Optional<AdminLoginAttemptDO> selectByUsername(@Param("username") String username);

    /**
     * 插入失败计数。
     *
     * @param attempt 计数记录
     */
    void insert(AdminLoginAttemptDO attempt);

    /**
     * 更新失败计数。
     *
     * @param attempt 计数记录
     * @return 受影响行数
     */
    int update(AdminLoginAttemptDO attempt);

    /**
     * 删除失败计数（登录成功时重置）。
     *
     * @param username 管理员账号
     * @return 受影响行数
     */
    int deleteByUsername(@Param("username") String username);
}
