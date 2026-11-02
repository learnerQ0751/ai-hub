package com.qtua.aihub.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 虚拟 Key 哈希工具。
 *
 * <p>虚拟 Key 为高熵随机串，服务端只保存 SHA-256 十六进制摘要；不保存明文，
 * 也不使用可逆加密。SHA-256 每次调用都重新获取实例，避免共享 {@link MessageDigest}
 * 带来的线程安全问题。</p>
 */
public final class KeyHasher {

    private static final String ALGORITHM = "SHA-256";

    private KeyHasher() {
    }

    /**
     * 计算 SHA-256 摘要并转为小写十六进制。
     *
     * @param plainKey 明文 Key
     * @return 64 位十六进制摘要
     */
    public static String sha256Hex(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashed = digest.digest(plainKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // JDK 必须内置 SHA-256；走到这里说明运行环境异常。
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
