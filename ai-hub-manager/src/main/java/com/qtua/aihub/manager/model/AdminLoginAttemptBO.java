package com.qtua.aihub.manager.model;

import java.time.Instant;

/**
 * 控制台登录失败业务对象。
 *
 * @param username    管理员账号
 * @param failedCount 连续失败次数
 * @param lockedUntil 锁定截止时间；为空表示未锁定
 */
public record AdminLoginAttemptBO(String username, int failedCount, Instant lockedUntil) {
}
