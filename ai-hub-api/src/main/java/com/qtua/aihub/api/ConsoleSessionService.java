package com.qtua.aihub.api;

import com.qtua.aihub.api.model.auth.ConsoleSessionAuthDTO;
import com.qtua.aihub.api.model.req.ConsoleLoginReqDTO;
import com.qtua.aihub.api.model.res.ConsoleSessionResDTO;

/**
 * 控制台管理员会话服务（仅 console 装载）。
 *
 * <p>账号密码来自配置；会话令牌只写 httpOnly cookie，库中只存令牌哈希。</p>
 */
public interface ConsoleSessionService {

    /**
     * 登录并创建会话。
     *
     * @param reqDTO 账号密码
     * @return 明文令牌与过期时间
     */
    ConsoleSessionAuthDTO login(ConsoleLoginReqDTO reqDTO);

    /**
     * 查询当前会话（无效或过期时抛 401）。
     *
     * @param token 明文会话令牌
     * @return 会话视图
     */
    ConsoleSessionResDTO current(String token);

    /**
     * 校验会话是否有效（过滤器使用，不抛异常）。
     *
     * @param token 明文会话令牌
     * @return true 表示有效
     */
    boolean isActive(String token);

    /**
     * 退出登录（幂等）。
     *
     * @param token 明文会话令牌
     */
    void logout(String token);
}
