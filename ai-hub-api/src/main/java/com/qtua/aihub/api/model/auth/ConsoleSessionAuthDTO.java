package com.qtua.aihub.api.model.auth;

import java.time.Instant;

/**
 * 登录成功后返回给控制器的会话凭据。
 *
 * <p>明文令牌只用于写入 httpOnly cookie，不得进入响应体或日志。</p>
 *
 * @param username  管理员账号
 * @param token     明文会话令牌
 * @param expiresAt 会话过期时间
 */
public record ConsoleSessionAuthDTO(String username, String token, Instant expiresAt) {
}
