package com.qtua.aihub.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PBKDF2 密码哈希工具测试。
 */
class Pbkdf2PasswordHasherTest {

    @Test
    void encoded_hash_matches_original_password() {
        String encoded = Pbkdf2PasswordHasher.encode("s3cret-pass");

        assertThat(encoded).startsWith("pbkdf2-sha256$");
        assertThat(Pbkdf2PasswordHasher.matches("s3cret-pass", encoded)).isTrue();
        assertThat(Pbkdf2PasswordHasher.matches("wrong", encoded)).isFalse();
    }

    @Test
    void random_salt_produces_different_hashes() {
        String first = Pbkdf2PasswordHasher.encode("same-password");
        String second = Pbkdf2PasswordHasher.encode("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(Pbkdf2PasswordHasher.matches("same-password", first)).isTrue();
        assertThat(Pbkdf2PasswordHasher.matches("same-password", second)).isTrue();
    }

    @Test
    void malformed_or_missing_hash_is_rejected() {
        assertThat(Pbkdf2PasswordHasher.matches("x", null)).isFalse();
        assertThat(Pbkdf2PasswordHasher.matches("x", "plain-text")).isFalse();
        assertThat(Pbkdf2PasswordHasher.matches("x", "bcrypt$10$abc$def")).isFalse();
    }
}
