package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.req.MemoryClearReqDTO;
import com.qtua.aihub.api.model.req.MemoryRecallReqDTO;
import com.qtua.aihub.api.model.req.MemorySaveReqDTO;
import com.qtua.aihub.api.model.res.MemoryClearResDTO;
import com.qtua.aihub.api.model.res.MemorySaveResDTO;
import com.qtua.aihub.common.config.MemoryProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.memory.MemoryQuery;
import com.qtua.aihub.manager.memory.MemoryManager;
import com.qtua.aihub.manager.model.MemoryBO;
import com.qtua.aihub.manager.model.MemoryClearResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * REQ-3.5 记忆服务单元测试：参数校验、TTL、跨项目授权、clear 结果与审计参数。
 */
class MemoryServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String PROJECT = "project-x";
    private static final String USER = "user-1";
    private static final String SESSION = "session-1";
    private static final String KEY_HASH = "key-hash";

    private MemoryManager memoryManager;
    private MemoryProperties properties;
    private MemoryServiceImpl memoryService;

    @BeforeEach
    void setUp() {
        memoryManager = mock(MemoryManager.class);
        properties = new MemoryProperties();
        memoryService = new MemoryServiceImpl(memoryManager, properties, new ObjectMapper());
    }

    @Test
    void save_builds_session_memory_with_ttl() {
        Instant before = Instant.now();

        MemorySaveResDTO response = memoryService.save(
                new MemorySaveReqDTO(TENANT, PROJECT, USER, SESSION, "用户对芒果过敏",
                        Map.of("source", "chat"), 3600L));

        ArgumentCaptor<MemoryBO> captor = ArgumentCaptor.forClass(MemoryBO.class);
        verify(memoryManager).save(captor.capture());
        MemoryBO saved = captor.getValue();
        assertThat(saved.memoryId()).isNotBlank();
        assertThat(saved.tenantId()).isEqualTo(TENANT);
        assertThat(saved.projectId()).isEqualTo(PROJECT);
        assertThat(saved.userId()).isEqualTo(USER);
        assertThat(saved.sessionId()).isEqualTo(SESSION);
        assertThat(saved.memoryType()).isEqualTo(MemoryBO.TYPE_SESSION);
        assertThat(saved.metadata()).containsEntry("source", "chat");
        assertThat(saved.createdAt()).isAfterOrEqualTo(before);
        assertThat(saved.expiresAt()).isAfter(before.plusSeconds(3500))
                .isBefore(Instant.now().plusSeconds(3700));
        assertThat(response.memoryId()).isEqualTo(saved.memoryId());
        assertThat(response.memoryType()).isEqualTo(MemoryBO.TYPE_SESSION);
        assertThat(response.expiresAt()).isEqualTo(saved.expiresAt());
    }

    @Test
    void save_without_session_is_user_level_memory() {
        memoryService.save(new MemorySaveReqDTO(TENANT, PROJECT, USER, null, "用户画像",
                null, null));

        ArgumentCaptor<MemoryBO> captor = ArgumentCaptor.forClass(MemoryBO.class);
        verify(memoryManager).save(captor.capture());
        MemoryBO saved = captor.getValue();
        assertThat(saved.sessionId()).isNull();
        assertThat(saved.memoryType()).isEqualTo(MemoryBO.TYPE_USER);
        assertThat(saved.expiresAt()).isNull();
        assertThat(saved.metadata()).isEmpty();
    }

    @Test
    void save_rejects_blank_required_fields() {
        assertInvalidParam(new MemorySaveReqDTO(" ", PROJECT, USER, null, "x", null, null),
                "tenant_id");
        assertInvalidParam(new MemorySaveReqDTO(TENANT, "", USER, null, "x", null, null),
                "project_id");
        assertInvalidParam(new MemorySaveReqDTO(TENANT, PROJECT, null, null, "x", null, null),
                "user_id");
        assertInvalidParam(new MemorySaveReqDTO(TENANT, PROJECT, USER, null, "  ", null, null),
                "content");
        verifyNoInteractions(memoryManager);
    }

    @Test
    void save_rejects_content_over_limit() {
        String content = "x".repeat(properties.getMaxContentLength() + 1);

        assertInvalidParam(new MemorySaveReqDTO(TENANT, PROJECT, USER, null, content, null, null),
                "content");
        verifyNoInteractions(memoryManager);
    }

    @Test
    void save_rejects_metadata_over_limit() {
        String huge = "x".repeat(properties.getMaxMetadataLength() + 1);

        assertInvalidParam(new MemorySaveReqDTO(TENANT, PROJECT, USER, null, "x",
                Map.of("blob", huge), null), "metadata");
        verifyNoInteractions(memoryManager);
    }

    @Test
    void save_rejects_invalid_ttl() {
        assertInvalidParam(new MemorySaveReqDTO(TENANT, PROJECT, USER, null, "x", null, 0L),
                "ttl_seconds");
        assertInvalidParam(new MemorySaveReqDTO(TENANT, PROJECT, USER, null, "x", null,
                properties.getMaxTtlSeconds() + 1), "ttl_seconds");
        verifyNoInteractions(memoryManager);
    }

    @Test
    void recall_rejects_cross_project_when_sharing_disabled() {
        MemoryRecallReqDTO request = new MemoryRecallReqDTO(TENANT, PROJECT, USER, null,
                null, null, null, null, true);

        assertThatThrownBy(() -> memoryService.recall(request))
                .isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException business = (ChatCompletionException) ex;
                    assertThat(business.getStatus().value()).isEqualTo(403);
                    assertThat(business.getType()).isEqualTo("memory_access_denied");
                    assertThat(business.getParam()).isEqualTo("cross_project");
                    assertThat(business.getMessage()).contains("跨项目");
                });
        verifyNoInteractions(memoryManager);
    }

    @Test
    void recall_rejects_cross_project_for_session_memory() {
        properties.setCrossProjectSharingEnabled(true);
        MemoryRecallReqDTO request = new MemoryRecallReqDTO(TENANT, PROJECT, USER, SESSION,
                null, null, null, null, true);

        assertInvalidParam(request, "session_id");
        verifyNoInteractions(memoryManager);
    }

    @Test
    void recall_applies_default_and_max_limit() {
        when(memoryManager.recall(any())).thenReturn(List.of());

        memoryService.recall(new MemoryRecallReqDTO(TENANT, PROJECT, USER, null,
                null, null, null, null, false));
        memoryService.recall(new MemoryRecallReqDTO(TENANT, PROJECT, USER, null,
                null, null, null, 500, false));

        ArgumentCaptor<MemoryQuery> captor = ArgumentCaptor.forClass(MemoryQuery.class);
        verify(memoryManager, org.mockito.Mockito.times(2)).recall(captor.capture());
        assertThat(captor.getAllValues().get(0).limit()).isEqualTo(properties.getDefaultRecallLimit());
        assertThat(captor.getAllValues().get(1).limit()).isEqualTo(properties.getMaxRecallLimit());
    }

    @Test
    void recall_rejects_invalid_time_range() {
        MemoryRecallReqDTO request = new MemoryRecallReqDTO(TENANT, PROJECT, USER, null,
                null, Instant.parse("2026-11-10T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"), 10, false);

        assertInvalidParam(request, "from");
        verifyNoInteractions(memoryManager);
    }

    @Test
    void recall_rejects_oversized_keyword() {
        MemoryRecallReqDTO request = new MemoryRecallReqDTO(TENANT, PROJECT, USER, null,
                "k".repeat(129), null, null, 10, false);

        assertInvalidParam(request, "keyword");
        verifyNoInteractions(memoryManager);
    }

    @Test
    void clear_returns_deleted_count_and_audit_id() {
        when(memoryManager.clear(any(), eq("用户行使删除权"), eq(KEY_HASH)))
                .thenReturn(new MemoryClearResult(3, "audit-1"));

        MemoryClearResDTO response = memoryService.clear(
                new MemoryClearReqDTO(TENANT, PROJECT, USER, SESSION, "用户行使删除权"),
                KEY_HASH);

        assertThat(response.deleted()).isEqualTo(3);
        assertThat(response.auditId()).isEqualTo("audit-1");
    }

    @Test
    void clear_requires_user_scope() {
        assertInvalidParam(new MemoryClearReqDTO(TENANT, PROJECT, null, null, null),
                "user_id");
        verifyNoInteractions(memoryManager);
    }

    private void assertInvalidParam(Object request, String param) {
        assertThatThrownBy(() -> {
            if (request instanceof MemorySaveReqDTO save) {
                memoryService.save(save);
            } else if (request instanceof MemoryRecallReqDTO recall) {
                memoryService.recall(recall);
            } else if (request instanceof MemoryClearReqDTO clear) {
                memoryService.clear(clear, KEY_HASH);
            }
        }).isInstanceOf(ChatCompletionException.class)
                .satisfies(ex -> {
                    ChatCompletionException business = (ChatCompletionException) ex;
                    assertThat(business.getStatus().value()).isEqualTo(400);
                    assertThat(business.getType()).isEqualTo("invalid_request_error");
                    assertThat(business.getParam()).isEqualTo(param);
                });
    }
}
