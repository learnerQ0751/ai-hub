package com.qtua.aihub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆服务配置（gateway.memory.*）。
 *
 * <p>默认项目隔离；跨项目共享需显式开启，且只影响用户级记忆（session_id 为空）。
 * TTL 在查询时惰性过滤，不依赖单机定时任务。</p>
 */
@ConfigurationProperties(prefix = "gateway.memory")
public class MemoryProperties {

    private static final long DEFAULT_MAX_TTL_SECONDS = 7_776_000L;
    private static final int DEFAULT_MAX_CONTENT_LENGTH = 10_000;
    private static final int DEFAULT_MAX_METADATA_LENGTH = 8_192;
    private static final int DEFAULT_RECALL_LIMIT = 20;
    private static final int DEFAULT_MAX_RECALL_LIMIT = 100;

    /** 是否允许用户级记忆跨项目共享；默认关闭（项目隔离）。 */
    private boolean crossProjectSharingEnabled = false;
    /** TTL 上限（秒），默认 90 天。 */
    private long maxTtlSeconds = DEFAULT_MAX_TTL_SECONDS;
    /** 单条记忆内容最大字符数。 */
    private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;
    /** metadata 序列化后的最大字符数。 */
    private int maxMetadataLength = DEFAULT_MAX_METADATA_LENGTH;
    /** 召回默认条数。 */
    private int defaultRecallLimit = DEFAULT_RECALL_LIMIT;
    /** 召回最大条数；超出按上限截断。 */
    private int maxRecallLimit = DEFAULT_MAX_RECALL_LIMIT;

    public boolean isCrossProjectSharingEnabled() {
        return crossProjectSharingEnabled;
    }

    public void setCrossProjectSharingEnabled(boolean crossProjectSharingEnabled) {
        this.crossProjectSharingEnabled = crossProjectSharingEnabled;
    }

    public long getMaxTtlSeconds() {
        return maxTtlSeconds;
    }

    public void setMaxTtlSeconds(long maxTtlSeconds) {
        this.maxTtlSeconds = maxTtlSeconds;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getMaxMetadataLength() {
        return maxMetadataLength;
    }

    public void setMaxMetadataLength(int maxMetadataLength) {
        this.maxMetadataLength = maxMetadataLength;
    }

    public int getDefaultRecallLimit() {
        return defaultRecallLimit;
    }

    public void setDefaultRecallLimit(int defaultRecallLimit) {
        this.defaultRecallLimit = defaultRecallLimit;
    }

    public int getMaxRecallLimit() {
        return maxRecallLimit;
    }

    public void setMaxRecallLimit(int maxRecallLimit) {
        this.maxRecallLimit = maxRecallLimit;
    }
}
