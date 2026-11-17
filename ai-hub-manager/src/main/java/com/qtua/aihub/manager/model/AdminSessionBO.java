package com.qtua.aihub.manager.model;

import java.time.Instant;

/**
 * 控制台会话业务对象。
 *
 * @param username  管理员账号
 * @param expiresAt 过期时间
 */
public record AdminSessionBO(String username, Instant expiresAt) {
}
