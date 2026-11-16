package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.McpToolService;
import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.McpToolDescriptor;
import com.qtua.aihub.common.mcp.TransportType;
import com.qtua.aihub.common.tenant.TenantContextHolder;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.trace.TraceEvent;
import com.qtua.aihub.common.trace.TraceRecorder;
import com.qtua.aihub.integration.mcp.McpToolClient;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.model.McpServerBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-4.2：MCP 调用与 REST 调用同构，记录 tenant/key/segment=mcp 的 Trace 事件。
 */
class McpCallServiceTraceTest {

    private static final String TENANT = "tenant-mcp";
    private static final String PROJECT = "project-mcp";
    private static final String KEY_HASH = "key-hash-mcp";

    private final List<TraceEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TenantIdentity.virtualKey(KEY_HASH, TENANT, PROJECT, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void successful_call_records_mcp_segment_with_tenant() {
        McpToolService toolService = mock(McpToolService.class);
        McpServerManager serverManager = mock(McpServerManager.class);
        McpToolClient client = mock(McpToolClient.class);
        McpToolDescriptor tool = McpToolDescriptor.of("srv-1", "srv", TENANT, "echo", "echo",
                "{}", true);
        when(toolService.find("srv__echo")).thenReturn(Optional.of(tool));
        when(serverManager.findByServerId("srv-1")).thenReturn(Optional.of(server()));
        when(client.callTool(any(), anyString(), anyString())).thenReturn("{\"ok\":true}");

        TraceRecorder recorder = events::add;
        McpCallServiceImpl service = new McpCallServiceImpl(toolService, serverManager, client,
                new ObjectMapper(), new TenantGuard(), recorder);

        String result = service.call("srv__echo", "{\"arguments\":{}}");

        assertThat(result).contains("ok");
        assertThat(events).hasSize(1);
        TraceEvent event = events.get(0);
        assertThat(event.segment()).isEqualTo("mcp");
        assertThat(event.name()).isEqualTo("srv__echo");
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.projectId()).isEqualTo(PROJECT);
        assertThat(event.keyHash()).isEqualTo(KEY_HASH);
        assertThat(event.status()).isEqualTo(200);
    }

    private McpServerBO server() {
        return new McpServerBO("srv-1", "srv", TransportType.HTTP,
                "http://localhost:1/mcp", null, List.of(), TENANT,
                McpServerStatus.ACTIVE, Instant.now(), null, null, Instant.now());
    }

    /**
     * 测试用守卫直接读上下文。
     */
    private static final class TenantGuard
            extends com.qtua.aihub.common.tenant.TenantScopeGuard {
    }
}
