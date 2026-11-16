package com.qtua.aihub;

import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.TransportType;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.mcp.McpToolCatalog;
import com.qtua.aihub.manager.model.McpServerBO;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 测试用 MCP 注册表工具（REQ-5.1）。
 *
 * <p>注册端点属于控制面（console）；数据面测试直接写注册表并置 ACTIVE，
 * 保持工具聚合/代理链路的真实验证。</p>
 */
public final class McpTestSupport {

    private McpTestSupport() {
    }

    /**
     * 直接登记一个 HTTP MCP Server 并标记 ACTIVE。
     *
     * @param manager    注册表门面
     * @param catalog    工具目录缓存
     * @param name       服务器名
     * @param endpoint   HTTP 端点
     * @param tenantId   租户
     * @return serverId
     */
    public static String register(McpServerManager manager, McpToolCatalog catalog,
                                  String name, String endpoint, String tenantId) {
        String serverId = UUID.randomUUID().toString();
        manager.save(new McpServerBO(serverId, name, TransportType.HTTP, endpoint, null,
                List.of(), tenantId, McpServerStatus.ACTIVE, Instant.now(), null, null,
                Instant.now()));
        catalog.invalidate();
        return serverId;
    }

    /**
     * 按名称删除登记项。
     *
     * @param manager 注册表门面
     * @param name    服务器名
     */
    public static void delete(McpServerManager manager, String name) {
        manager.findByName(name).ifPresent(server -> manager.delete(server.serverId()));
    }
}
