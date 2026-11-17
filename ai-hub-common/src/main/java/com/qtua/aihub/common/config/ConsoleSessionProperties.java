package com.qtua.aihub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * 控制台管理员会话配置。
 *
 * <p>账号密码来自配置（密码只存 PBKDF2 哈希）；会话 cookie 为 httpOnly + SameSite=Lax，
 * Secure 属性可配置，便于本地 http 演示时关闭。</p>
 */
@ConfigurationProperties(prefix = "console.session")
public class ConsoleSessionProperties {

    private static final int DEFAULT_TIMEOUT_HOURS = 2;
    private static final int DEFAULT_LOCK_SECONDS = 30;
    private static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;

    /** 是否启用控制台会话登录；关闭时只接受 master key。 */
    private boolean enabled = false;
    /** 管理员账号。 */
    private String username;
    /** 管理员密码哈希（pbkdf2-sha256$迭代次数$盐$摘要）。 */
    private String passwordHash;
    /** 会话 cookie 名称。 */
    private String cookieName = "aihub_admin_session";
    /** cookie 是否带 Secure；本地 http 演示可关闭。 */
    private boolean cookieSecure = true;
    /** 会话有效期（自登录起算）。 */
    private Duration timeout = Duration.ofHours(DEFAULT_TIMEOUT_HOURS);
    /** 连续失败多少次后锁定。 */
    private int maxFailedAttempts = DEFAULT_MAX_FAILED_ATTEMPTS;
    /** 锁定时长。 */
    private Duration lockDuration = Duration.ofSeconds(DEFAULT_LOCK_SECONDS);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    /**
     * 生效的会话有效期（空或非正数回退 2 小时）。
     *
     * @return 有效期
     */
    public Duration effectiveTimeout() {
        return Objects.isNull(timeout) || timeout.isZero() || timeout.isNegative()
                ? Duration.ofHours(DEFAULT_TIMEOUT_HOURS) : timeout;
    }

    /**
     * 生效的锁定时长（空或非正数回退 30 秒）。
     *
     * @return 锁定时长
     */
    public Duration effectiveLockDuration() {
        return Objects.isNull(lockDuration) || lockDuration.isZero() || lockDuration.isNegative()
                ? Duration.ofSeconds(DEFAULT_LOCK_SECONDS) : lockDuration;
    }

    /**
     * 生效的失败阈值（非正数回退 5 次）。
     *
     * @return 失败阈值
     */
    public int effectiveMaxFailedAttempts() {
        return maxFailedAttempts > 0 ? maxFailedAttempts : DEFAULT_MAX_FAILED_ATTEMPTS;
    }
}
