package com.qtua.aihub.api.model.res;

/**
 * 封禁/解封虚拟 Key 响应。
 *
 * @param key     掩码后的 Key
 * @param blocked 当前封禁状态
 */
public record KeyBlockResDTO(String key, boolean blocked) {
}
