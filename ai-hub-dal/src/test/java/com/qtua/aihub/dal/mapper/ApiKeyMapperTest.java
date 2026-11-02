package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.ApiKeyDO;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
@ContextConfiguration(classes = ApiKeyMapperTest.TestConfig.class)
@MapperScan("com.qtua.aihub.dal.mapper")
@Sql(scripts = {"/db/h2-jsonb-domain.sql", "/db/api-keys-schema.sql"})
class ApiKeyMapperTest {

    /**
     * DAL 模块没有启动类，提供一个测试用配置锚点。
     */
    @SpringBootConfiguration
    static class TestConfig {
    }

    private static final String KEY_HASH =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    @Test
    void saves_and_reads_all_fields() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ApiKeyDO apiKey = apiKey(now.plus(1, ChronoUnit.HOURS));
        apiKey.setMetadata("{\"user\":\"tester\"}");

        apiKeyMapper.insert(apiKey);

        Optional<ApiKeyDO> found = apiKeyMapper.selectByKeyHash(KEY_HASH);
        assertThat(found).isPresent();
        ApiKeyDO stored = found.orElseThrow();
        assertThat(stored.getKeyHash()).isEqualTo(KEY_HASH);
        assertThat(stored.getModels()).contains("chat");
        assertThat(stored.getMetadata()).contains("tester");
        assertThat(stored.getSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stored.isBlocked()).isFalse();
        assertThat(stored.getExpiresAt()).isEqualTo(now.plus(1, ChronoUnit.HOURS));
        assertThat(stored.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void stores_only_hash_not_plaintext() {
        apiKeyMapper.insert(apiKey(null));

        assertThat(apiKeyMapper.selectByKeyHash("sk-plaintext")).isEmpty();
        assertThat(apiKeyMapper.selectByKeyHash(KEY_HASH)).isPresent();
    }

    @Test
    void updates_blocked_flag() {
        apiKeyMapper.insert(apiKey(null));

        int updated = apiKeyMapper.updateBlocked(KEY_HASH, true);

        assertThat(updated).isEqualTo(1);
        assertThat(apiKeyMapper.selectByKeyHash(KEY_HASH).orElseThrow().isBlocked()).isTrue();
    }

    @Test
    void returns_empty_for_unknown_hash() {
        assertThat(apiKeyMapper.selectByKeyHash("none")).isEmpty();
    }

    private ApiKeyDO apiKey(Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ApiKeyDO apiKey = new ApiKeyDO();
        apiKey.setKeyHash(KEY_HASH);
        apiKey.setModels("[\"chat\"]");
        apiKey.setSpend(BigDecimal.ZERO);
        apiKey.setExpiresAt(expiresAt);
        apiKey.setBlocked(false);
        apiKey.setCreatedAt(now);
        return apiKey;
    }
}
