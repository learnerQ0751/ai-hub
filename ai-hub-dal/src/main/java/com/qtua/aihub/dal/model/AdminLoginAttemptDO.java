package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `admin_login_attempts` 表数据对象：登录失败计数与锁定截止时间。
 */
public class AdminLoginAttemptDO {

    private String username;
    private int failedCount;
    private Instant lockedUntil;
    private Instant updatedAt;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
