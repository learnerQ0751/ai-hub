package com.qtua.aihub.api.model.req;

/**
 * 封禁/解封虚拟 Key 请求。
 *
 * @param key 虚拟 Key 明文
 */
public record KeyBlockReqDTO(String key) {
}
