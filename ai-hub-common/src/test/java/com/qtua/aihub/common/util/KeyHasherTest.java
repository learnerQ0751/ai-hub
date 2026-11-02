package com.qtua.aihub.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyHasherTest {

    @Test
    void produces_standard_sha256_hex() {
        assertThat(KeyHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void is_deterministic_and_64_characters() {
        String first = KeyHasher.sha256Hex("sk-demo");
        String second = KeyHasher.sha256Hex("sk-demo");

        assertThat(first).isEqualTo(second).hasSize(64);
    }
}
