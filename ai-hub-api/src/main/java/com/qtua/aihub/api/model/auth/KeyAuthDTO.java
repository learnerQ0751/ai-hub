package com.qtua.aihub.api.model.auth;

import java.util.List;
import java.util.Objects;

/**
 * 已认证调用方上下文。
 *
 * <p>由 AuthFilter 写入请求属性，Controller 读取后传给 Service；
 * 不包含明文 Key。</p>
 *
 * @param keyHash   虚拟 Key 的 SHA-256 摘要；master key 调用时为 null
 * @param models    虚拟 Key 的模型白名单；空表示全部
 * @param masterKey 是否为 master key 调用
 */
public record KeyAuthDTO(String keyHash, List<String> models, boolean masterKey) {

    /**
     * 防御性复制白名单。
     */
    public KeyAuthDTO {
        models = Objects.isNull(models) ? List.of() : List.copyOf(models);
    }

    /**
     * master key 调用方（全权限）。
     *
     * @return 认证上下文
     */
    public static KeyAuthDTO master() {
        return new KeyAuthDTO(null, List.of(), true);
    }

    /**
     * 虚拟 Key 调用方。
     *
     * @param keyHash SHA-256 摘要
     * @param models  模型白名单
     * @return 认证上下文
     */
    public static KeyAuthDTO virtualKey(String keyHash, List<String> models) {
        return new KeyAuthDTO(keyHash, models, false);
    }
}
