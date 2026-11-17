package com.qtua.aihub.api.model.req;

/**
 * 控制台管理员登录请求。
 *
 * @param username 管理员账号
 * @param password 明文密码（仅用于本次比对，不落库、不记录日志）
 */
public record ConsoleLoginReqDTO(String username, String password) {
}
