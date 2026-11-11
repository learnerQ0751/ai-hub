package com.qtua.aihub.manager.memory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.memory.MemoryEntry;
import com.qtua.aihub.common.memory.MemoryQuery;
import com.qtua.aihub.dal.mapper.MemoryAuditMapper;
import com.qtua.aihub.dal.mapper.MemoryMapper;
import com.qtua.aihub.dal.model.MemoryDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 记忆数据门面测试：隐私字段不进入日志明文。
 */
class MemoryManagerTest {

    private static final String SENSITIVE = "secret-token-123";
    private static final String TENANT = "tenant-a";
    private static final String PROJECT = "project-x";
    private static final String USER = "user-1";

    private final Logger logger = (Logger) LoggerFactory.getLogger(MemoryManager.class);
    private ListAppender<ILoggingEvent> appender;
    private MemoryMapper memoryMapper;
    private MemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        memoryMapper = mock(MemoryMapper.class);
        memoryManager = new MemoryManager(memoryMapper, mock(MemoryAuditMapper.class),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void corrupted_metadata_is_not_logged_in_plaintext() {
        MemoryDO dataObject = new MemoryDO();
        dataObject.setMemoryId("m1");
        dataObject.setTenantId(TENANT);
        dataObject.setProjectId(PROJECT);
        dataObject.setUserId(USER);
        dataObject.setMemoryType("USER");
        dataObject.setContent("用户画像");
        dataObject.setMetadata("{\"profile\": " + SENSITIVE + "}");
        dataObject.setCreatedAt(Instant.now());
        when(memoryMapper.selectForRecall(any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), anyInt())).thenReturn(List.of(dataObject));

        List<MemoryEntry> entries = memoryManager.recall(new MemoryQuery(TENANT, PROJECT, USER,
                null, null, null, null, 10, false));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).metadata()).isEmpty();
        String logs = appender.list.stream()
                .map(event -> event.getFormattedMessage() + "|"
                        + (event.getThrowableProxy() == null
                        ? "" : event.getThrowableProxy().getMessage()))
                .collect(Collectors.joining("\n"));
        assertThat(logs).contains("invalid memory metadata JSON");
        assertThat(logs).doesNotContain(SENSITIVE);
    }
}
