package com.qtua.aihub.dal.mapper;

import com.qtua.aihub.dal.model.MemoryAuditDO;
import com.qtua.aihub.dal.model.MemoryDO;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.5 memories / memory_audit_logs Mapper 测试：过期过滤、关键词、作用域与跨项目。
 */
@MybatisTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
@ContextConfiguration(classes = MemoryMapperTest.TestConfig.class)
@MapperScan("com.qtua.aihub.dal.mapper")
@Sql(scripts = {"/db/h2-jsonb-domain.sql", "/db/memories-schema.sql"})
class MemoryMapperTest {

    /**
     * DAL 模块没有启动类，提供一个测试用配置锚点。
     */
    @SpringBootConfiguration
    static class TestConfig {
    }

    private static final String TENANT = "t1";
    private static final String PROJECT = "p1";
    private static final String USER = "u1";

    @Autowired
    private MemoryMapper memoryMapper;

    @Autowired
    private MemoryAuditMapper memoryAuditMapper;

    @Test
    void inserts_and_recalls_by_scope() {
        memoryMapper.insert(memory("m1", PROJECT, USER, "s1", "用户对芒果过敏", null));
        memoryMapper.insert(memory("m2", PROJECT, USER, null, "用户画像", null));

        List<MemoryDO> session = memoryMapper.selectForRecall(TENANT, PROJECT, USER, "s1",
                null, null, null, false, 10);
        assertThat(session).extracting(MemoryDO::getMemoryId).containsExactly("m1");

        List<MemoryDO> userLevel = memoryMapper.selectForRecall(TENANT, PROJECT, USER, null,
                null, null, null, false, 10);
        assertThat(userLevel).extracting(MemoryDO::getMemoryId).containsExactly("m2");

        assertThat(memoryMapper.selectForRecall(TENANT, PROJECT, "u2", null,
                null, null, null, false, 10)).isEmpty();
    }

    @Test
    void filters_expired_and_keyword() {
        memoryMapper.insert(memory("m1", PROJECT, USER, null, "用户对芒果过敏", null));
        memoryMapper.insert(memory("m2", PROJECT, USER, null, "用户偏好深色主题",
                Instant.now().minus(1, ChronoUnit.HOURS)));

        List<MemoryDO> active = memoryMapper.selectForRecall(TENANT, PROJECT, USER, null,
                null, null, null, false, 10);
        assertThat(active).extracting(MemoryDO::getMemoryId).containsExactly("m1");

        List<MemoryDO> keyword = memoryMapper.selectForRecall(TENANT, PROJECT, USER, null,
                "%芒果%", null, null, false, 10);
        assertThat(keyword).extracting(MemoryDO::getMemoryId).containsExactly("m1");
    }

    @Test
    void cross_project_only_returns_user_level_memories() {
        memoryMapper.insert(memory("m1", PROJECT, USER, null, "项目 A 画像", null));
        memoryMapper.insert(memory("m2", "p2", USER, null, "项目 B 画像", null));
        memoryMapper.insert(memory("m3", PROJECT, USER, "s1", "项目 A 会话", null));

        List<MemoryDO> shared = memoryMapper.selectForRecall(TENANT, "p2", USER, null,
                null, null, null, true, 10);
        assertThat(shared).extracting(MemoryDO::getMemoryId).containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    void deletes_by_scope_and_audits_physically() {
        memoryMapper.insert(memory("m1", PROJECT, USER, "s1", "会话", null));
        memoryMapper.insert(memory("m2", PROJECT, USER, null, "画像", null));
        memoryMapper.insert(memory("m3", PROJECT, "u2", null, "他人", null));

        List<String> ids = memoryMapper.selectIdsForClear(TENANT, PROJECT, USER, null, 100);
        assertThat(ids).containsExactlyInAnyOrder("m1", "m2");

        int deleted = memoryMapper.deleteByScope(TENANT, PROJECT, USER, null);
        assertThat(deleted).isEqualTo(2);

        MemoryAuditDO audit = new MemoryAuditDO();
        audit.setAuditId("audit-1");
        audit.setTenantId(TENANT);
        audit.setProjectId(PROJECT);
        audit.setUserId(USER);
        audit.setSessionId(null);
        audit.setOperation("CLEAR");
        audit.setDeletedCount(deleted);
        audit.setMemoryIds("[\"m1\",\"m2\"]");
        audit.setOperatorKey("hash");
        audit.setReason("用户行使删除权");
        audit.setCreatedAt(Instant.now());
        memoryAuditMapper.insert(audit);

        Optional<MemoryAuditDO> stored = memoryAuditMapper.selectByAuditId("audit-1");
        assertThat(stored).isPresent();
        assertThat(stored.orElseThrow().getDeletedCount()).isEqualTo(2);
        assertThat(stored.orElseThrow().getMemoryIds()).contains("m1").contains("m2");
    }

    private MemoryDO memory(String memoryId, String projectId, String userId, String sessionId,
                            String content, Instant expiresAt) {
        MemoryDO memory = new MemoryDO();
        memory.setMemoryId(memoryId);
        memory.setTenantId(TENANT);
        memory.setProjectId(projectId);
        memory.setUserId(userId);
        memory.setSessionId(sessionId);
        memory.setMemoryType(sessionId == null ? "USER" : "SESSION");
        memory.setContent(content);
        memory.setMetadata("{\"source\":\"test\"}");
        memory.setExpiresAt(expiresAt);
        memory.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return memory;
    }
}
