package com.qtua.aihub;

import com.qtua.aihub.api.McpServerService;
import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.TransportType;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.model.McpServerBO;
import com.qtua.aihub.service.admin.McpServerStartupLoader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-2.1 启动加载器单元测试：ACTIVE Server 启动时重新探活，空表不调用。
 */
class McpServerStartupLoaderTest {

    private final McpServerManager manager = mock(McpServerManager.class);
    private final McpServerService service = mock(McpServerService.class);
    private final McpServerStartupLoader loader = new McpServerStartupLoader(manager, service);

    @Test
    void reprobes_each_active_server_on_startup() {
        when(manager.findActive()).thenReturn(List.of(server("a", "id-a"), server("b", "id-b")));

        loader.run(null);

        verify(service, times(1)).reprobe("id-a");
        verify(service, times(1)).reprobe("id-b");
    }

    @Test
    void does_nothing_when_no_active_server() {
        when(manager.findActive()).thenReturn(List.of());

        loader.run(null);

        verify(service, never()).reprobe(anyString());
    }

    private McpServerBO server(String name, String serverId) {
        return new McpServerBO(serverId, name, TransportType.HTTP, "http://127.0.0.1:1/mcp",
                null, List.of(), null, McpServerStatus.ACTIVE, Instant.now(),
                null, null, Instant.now());
    }
}
