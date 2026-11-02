package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.CallLogDO;
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

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
@ContextConfiguration(classes = CallLogMapperTest.TestConfig.class)
@MapperScan("com.qtua.aihub.dal.mapper")
@Sql(scripts = {"/db/h2-jsonb-domain.sql", "/db/call-logs-schema.sql"})
class CallLogMapperTest {

    /**
     * DAL 模块没有启动类，提供一个测试用配置锚点。
     */
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    private CallLogMapper callLogMapper;

    @Test
    void saves_and_reads_all_fields() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        CallLogDO callLog = new CallLogDO();
        callLog.setRequestId("req-1");
        callLog.setKeyId("key-hash");
        callLog.setModelAlias("chat");
        callLog.setDeploymentModel("deepseek-chat");
        callLog.setPromptTokens(10);
        callLog.setCompletionTokens(20);
        callLog.setTotalTokens(30);
        callLog.setCost(new BigDecimal("0.00001234"));
        callLog.setLatencyMs(321L);
        callLog.setStatus(200);
        callLog.setTenantId("tenant-a");
        callLog.setCreatedAt(now);

        callLogMapper.insert(callLog);

        CallLogDO stored = callLogMapper.selectByRequestId("req-1").orElseThrow();
        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getKeyId()).isEqualTo("key-hash");
        assertThat(stored.getModelAlias()).isEqualTo("chat");
        assertThat(stored.getDeploymentModel()).isEqualTo("deepseek-chat");
        assertThat(stored.getPromptTokens()).isEqualTo(10);
        assertThat(stored.getCompletionTokens()).isEqualTo(20);
        assertThat(stored.getTotalTokens()).isEqualTo(30);
        assertThat(stored.getCost()).isEqualByComparingTo("0.00001234");
        assertThat(stored.getLatencyMs()).isEqualTo(321L);
        assertThat(stored.getStatus()).isEqualTo(200);
        assertThat(stored.getTenantId()).isEqualTo("tenant-a");
        assertThat(stored.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void allows_master_call_without_key_id() {
        CallLogDO callLog = new CallLogDO();
        callLog.setRequestId("req-master");
        callLog.setModelAlias("chat");
        callLog.setStatus(200);
        callLog.setCreatedAt(Instant.now());

        callLogMapper.insert(callLog);

        assertThat(callLogMapper.selectByRequestId("req-master").orElseThrow().getKeyId()).isNull();
    }

    @Test
    void returns_empty_for_unknown_request_id() {
        assertThat(callLogMapper.selectByRequestId("missing")).isEmpty();
    }

    @Test
    void counts_logs_by_key_id() {
        CallLogDO first = new CallLogDO();
        first.setRequestId("req-1");
        first.setKeyId("key-hash");
        first.setModelAlias("chat");
        first.setStatus(200);
        first.setCreatedAt(Instant.now());
        callLogMapper.insert(first);

        CallLogDO second = new CallLogDO();
        second.setRequestId("req-2");
        second.setKeyId("key-hash");
        second.setModelAlias("chat");
        second.setStatus(403);
        second.setCreatedAt(Instant.now());
        callLogMapper.insert(second);

        assertThat(callLogMapper.countByKeyId("key-hash")).isEqualTo(2);
        assertThat(callLogMapper.countByKeyId("other")).isZero();
    }
}
