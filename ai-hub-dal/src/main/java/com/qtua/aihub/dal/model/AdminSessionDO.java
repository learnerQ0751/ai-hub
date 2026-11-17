package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `admin_sessions` 表数据对象。
 *
 * <p>只保存会话令牌的哈希；明文令牌仅存在于客户端 cookie。</p>
 */
public class AdminSessionDO {

    private Long id;
    private String tokenHash;
    private String username;
    private Instant createdAt;
    private Instant expiresAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
