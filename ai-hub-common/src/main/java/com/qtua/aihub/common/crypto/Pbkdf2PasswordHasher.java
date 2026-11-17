package com.qtua.aihub.common.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

/**
 * 管理员密码 PBKDF2-SHA256 哈希工具（JDK 内置，不引入额外依赖）。
 *
 * <p>编码格式：{@code pbkdf2-sha256$迭代次数$Base64盐$Base64摘要}。
 * 每次编码使用随机盐；校验使用常量时间比较。</p>
 */
public final class Pbkdf2PasswordHasher {

    private static final String PREFIX = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 210000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int FIELD_COUNT = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private Pbkdf2PasswordHasher() {
    }

    /**
     * 生成密码哈希（随机盐，20 万次迭代）。
     *
     * @param password 明文密码
     * @return 编码后的哈希
     */
    public static String encode(String password) {
        if (Objects.isNull(password) || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt, DEFAULT_ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + DEFAULT_ITERATIONS + "$" + encoder.encodeToString(salt)
                + "$" + encoder.encodeToString(hash);
    }

    /**
     * 校验明文密码与编码哈希是否匹配。
     *
     * @param password 明文密码
     * @param encoded  编码后的哈希
     * @return true 表示匹配；格式非法或密码不匹配均为 false
     */
    public static boolean matches(String password, String encoded) {
        if (Objects.isNull(password) || Objects.isNull(encoded)) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != FIELD_COUNT || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[parts.length - 1]);
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            // JDK 必须内置 PBKDF2WithHmacSHA256；走到这里说明运行环境异常。
            throw new IllegalStateException("PBKDF2 algorithm is unavailable", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
