package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 控制台会话视图（不含令牌；令牌只写 httpOnly cookie）。
 *
 * @param username  管理员账号
 * @param expiresAt 会话过期时间（ISO-8601）
 */
public record ConsoleSessionResDTO(String username,
                                   @JsonProperty("expires_at") String expiresAt) {
}
