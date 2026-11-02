package com.qtua.aihub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流与预算配置。
 *
 * <p>限流计数在 Redis；Redis 故障时默认 fail-open（放行并告警），
 * 可通过 {@code gateway.rate-limit.fail-open=false} 改为 fail-closed。</p>
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /** 默认限流窗口：60 秒。 */
    private static final int DEFAULT_WINDOW_SECONDS = 60;
    /** 默认输出 Token 上限：1024。 */
    private static final int DEFAULT_MAX_TOKENS = 1024;

    /** 是否启用 Key 级限流与预算。 */
    private boolean enabled = true;
    /** Redis 故障时是否放行。 */
    private boolean failOpen = true;
    /** 限流窗口（秒）。 */
    private int windowSeconds = DEFAULT_WINDOW_SECONDS;
    /** 请求未携带 max_tokens 时用于 TPM 预占的默认输出上限。 */
    private int defaultMaxTokens = DEFAULT_MAX_TOKENS;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getDefaultMaxTokens() {
        return defaultMaxTokens;
    }

    public void setDefaultMaxTokens(int defaultMaxTokens) {
        this.defaultMaxTokens = defaultMaxTokens;
    }
}
