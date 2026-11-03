package com.qtua.aihub.service.mcp;

import com.qtua.aihub.api.McpServerService;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.model.McpServerBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动加载器：加载 DB 中 ACTIVE 的 MCP Server 并重新探活。
 *
 * <p>多实例说明：每个实例启动都会各自探活一遍，探活结果写同一张 DB
 * （status/last_health_at），无本地状态，重复探活只是额外开销，不产生一致性问题。
 * 探活失败不会阻断应用启动。</p>
 */
@Component
public class McpServerStartupLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerStartupLoader.class);

    private final McpServerManager mcpServerManager;
    private final McpServerService mcpServerService;

    /**
     * 构造加载器。
     *
     * @param mcpServerManager MCP Server 数据门面
     * @param mcpServerService MCP Server 服务
     */
    public McpServerStartupLoader(McpServerManager mcpServerManager,
                                  McpServerService mcpServerService) {
        this.mcpServerManager = mcpServerManager;
        this.mcpServerService = mcpServerService;
    }

    /**
     * 启动时加载并探活 ACTIVE Server。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        List<McpServerBO> activeServers = mcpServerManager.findActive();
        if (activeServers.isEmpty()) {
            LOG.info("no ACTIVE mcp server to load at startup");
            return;
        }
        LOG.info("loading {} ACTIVE mcp server(s) at startup", activeServers.size());
        for (McpServerBO server : activeServers) {
            try {
                mcpServerService.reprobe(server.serverId());
            } catch (RuntimeException ex) {
                LOG.warn("startup probe failed: name={}, error={}", server.name(), ex.getMessage());
            }
        }
    }
}
