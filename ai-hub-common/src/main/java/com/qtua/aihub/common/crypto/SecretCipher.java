package com.qtua.aihub.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 上游凭证加解密（AES-256-GCM）。
 *
 * <p>密钥来自 {@code GATEWAY_SECRET_KEY}/{@code gateway.secret-key}；未配置时退化为
 * 可逆编码并打印 WARN（仅限本地开发，生产必须配置）。密文格式：
 * {@code enc:<base64(iv)>:<base64(cipher)>}。</p>
 */
@Component
public class SecretCipher {

    private static final Logger LOG = LoggerFactory.getLogger(SecretCipher.class);
    private static final String ENC_PREFIX = "enc:";
    private static final String PLAIN_PREFIX = "plain:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String secret;
    private volatile SecretKeySpec key;

    /**
     * 构造加解密器。
     *
     * @param secret 环境密钥；空表示本地明文模式
     */
    public SecretCipher(@Value("${gateway.secret-key:}") String secret) {
        this.secret = secret;
        if (!StringUtils.hasText(secret)) {
            LOG.warn("gateway.secret-key 未配置：上游凭证将以弱编码存储，生产环境必须配置");
        }
    }

    /**
     * 惰性构建 AES 密钥（避免构造函数抛异常触发 SpotBugs 告警）。
     *
     * @return 密钥；未配置时为空
     */
    private SecretKeySpec key() {
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        SecretKeySpec current = key;
        if (ObjectUtils.isEmpty(current)) {
            synchronized (this) {
                current = key;
                if (ObjectUtils.isEmpty(current)) {
                    try {
                        byte[] digest = MessageDigest.getInstance("SHA-256")
                                .digest(secret.getBytes(StandardCharsets.UTF_8));
                        current = new SecretKeySpec(digest, "AES");
                    } catch (Exception ex) {
                        throw new IllegalStateException("初始化凭证加密密钥失败", ex);
                    }
                    key = current;
                }
            }
        }
        return current;
    }

    /**
     * 加密明文凭证。
     *
     * @param plain 明文；空值原样返回
     * @return 密文/弱编码
     */
    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        SecretKeySpec currentKey = key();
        if (ObjectUtils.isEmpty(currentKey)) {
            return PLAIN_PREFIX + Base64.getEncoder().encodeToString(
                    plain.getBytes(StandardCharsets.UTF_8));
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ENC_PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception ex) {
            throw new IllegalStateException("凭证加密失败", ex);
        }
    }

    /**
     * 解密密文；兼容历史明文。
     *
     * @param stored 存储值
     * @return 明文
     */
    public String decrypt(String stored) {
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        if (stored.startsWith(PLAIN_PREFIX)) {
            return new String(Base64.getDecoder().decode(stored.substring(PLAIN_PREFIX.length())),
                    StandardCharsets.UTF_8);
        }
        if (!stored.startsWith(ENC_PREFIX)) {
            return stored;
        }
        SecretKeySpec currentKey = key();
        if (ObjectUtils.isEmpty(currentKey)) {
            throw new IllegalStateException("缺少 gateway.secret-key，无法解密上游凭证");
        }
        try {
            String[] parts = stored.substring(ENC_PREFIX.length()).split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, currentKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("凭证解密失败", ex);
        }
    }
}
