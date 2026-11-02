package com.qtua.aihub.api;

import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.KeyGenerateReqDTO;
import com.qtua.aihub.api.model.res.KeyBlockResDTO;
import com.qtua.aihub.api.model.res.KeyGenerateResDTO;
import com.qtua.aihub.api.model.res.KeyInfoResDTO;

/**
 * 虚拟 Key 应用服务契约。
 *
 * <p>管理面仅 master key 可调用（由 AuthFilter 保证）；{@link #authenticate(String)}
 * 供数据面鉴权使用。</p>
 */
public interface KeyService {

    /**
     * 生成虚拟 Key（仅 master key）。
     *
     * @param reqDTO 生成参数
     * @return 含一次性明文 Key 的响应
     */
    KeyGenerateResDTO generate(KeyGenerateReqDTO reqDTO);

    /**
     * 校验虚拟 Key 并返回认证上下文。
     *
     * @param plainKey 明文 Key
     * @return 认证上下文
     */
    KeyAuthDTO authenticate(String plainKey);

    /**
     * 查询 Key 信息（仅 master key）。
     *
     * @param plainKey 明文 Key
     * @return Key 信息（Key 字段已掩码）
     */
    KeyInfoResDTO info(String plainKey);

    /**
     * 封禁 Key（仅 master key）。
     *
     * @param plainKey 明文 Key
     * @return 封禁结果
     */
    KeyBlockResDTO block(String plainKey);

    /**
     * 解封 Key（仅 master key）。
     *
     * @param plainKey 明文 Key
     * @return 解封结果
     */
    KeyBlockResDTO unblock(String plainKey);
}
