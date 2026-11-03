package com.qtua.aihub.service;

import com.qtua.aihub.api.McpToolService;
import com.qtua.aihub.api.model.res.McpToolListResDTO;
import com.qtua.aihub.api.model.res.McpToolResDTO;
import com.qtua.aihub.common.mcp.McpToolDescriptor;
import com.qtua.aihub.common.mcp.ToolAggregationResult;
import com.qtua.aihub.core.mcp.McpToolAggregator;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.mcp.McpToolCatalog;
import com.qtua.aihub.manager.model.McpServerBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MCP 工具发现实现。
 *
 * <p>缓存编排在 service：快照新鲜（TTL 60s）直接返回；过期或缺失时触发聚合。
 * 变更（added/removed）写应用日志，供 4.x 审计接入（需求卡 2.2.4）。</p>
 */
@Service
public class McpToolServiceImpl implements McpToolService {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolServiceImpl.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final McpToolCatalog mcpToolCatalog;
    private final McpToolAggregator mcpToolAggregator;
    private final McpServerManager mcpServerManager;

    /**
     * 构造服务。
     *
     * @param mcpToolCatalog   工具目录快照缓存
     * @param mcpToolAggregator 工具聚合器
     * @param mcpServerManager MCP Server 数据门面
     */
    public McpToolServiceImpl(McpToolCatalog mcpToolCatalog, McpToolAggregator mcpToolAggregator,
                              McpServerManager mcpServerManager) {
        this.mcpToolCatalog = mcpToolCatalog;
        this.mcpToolAggregator = mcpToolAggregator;
        this.mcpServerManager = mcpServerManager;
    }

    /**
     * 查询工具目录。
     *
     * @param serverIdOrName Server ID 或名称（可空）
     * @param tenantId       租户 ID（可空）
     * @return 工具目录（含降级信息）
     */
    @Override
    public McpToolListResDTO list(String serverIdOrName, String tenantId) {
        McpToolCatalog.Snapshot snapshot = ensureSnapshot();
        List<McpToolResDTO> tools = snapshot.tools().stream()
                .filter(tool -> matchesServer(tool, serverIdOrName))
                .filter(tool -> matchesTenant(tool, tenantId))
                .map(this::toResDTO)
                .toList();
        return new McpToolListResDTO(tools, snapshot.degradedServers(),
                "Successfully retrieved tools");
    }

    /**
     * 手动刷新工具目录（忽略 TTL）。
     */
    @Override
    public void refresh() {
        McpToolCatalog.Snapshot previous = mcpToolCatalog.current().orElse(null);
        List<McpServerBO> activeServers = mcpServerManager.findActive();
        ToolAggregationResult result = mcpToolAggregator.aggregate(activeServers);
        McpToolCatalog.Snapshot next = new McpToolCatalog.Snapshot(
                result.tools(), result.degradedServers(), Instant.now());
        logChanges(previous, next);
        mcpToolCatalog.save(next);
        LOG.info("mcp tool catalog refreshed: tools={}, degraded={}, servers={}",
                next.tools().size(), next.degradedServers(), activeServers.size());
    }

    private McpToolCatalog.Snapshot ensureSnapshot() {
        if (mcpToolCatalog.isFresh(TTL)) {
            return mcpToolCatalog.current().orElseThrow();
        }
        refresh();
        return mcpToolCatalog.current().orElseThrow();
    }

    private boolean matchesServer(McpToolDescriptor tool, String serverIdOrName) {
        if (!StringUtils.hasText(serverIdOrName)) {
            return true;
        }
        return serverIdOrName.equals(tool.serverId()) || serverIdOrName.equals(tool.serverName());
    }

    private boolean matchesTenant(McpToolDescriptor tool, String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return true;
        }
        return Objects.isNull(tool.tenantId()) || tenantId.equals(tool.tenantId());
    }

    private McpToolResDTO toResDTO(McpToolDescriptor tool) {
        return new McpToolResDTO(tool.exposedName(), tool.toolName(), tool.description(),
                tool.inputSchema(), tool.serverId(), tool.serverName(), tool.tenantId());
    }

    /**
     * 记录工具目录变更（added/removed），满足审计要求。
     *
     * @param previous 旧快照（可为空）
     * @param next     新快照
     */
    private void logChanges(McpToolCatalog.Snapshot previous, McpToolCatalog.Snapshot next) {
        Set<String> oldNames = new HashSet<>();
        if (Objects.nonNull(previous)) {
            previous.tools().forEach(tool -> oldNames.add(tool.exposedName()));
        }
        Set<String> newNames = new HashSet<>();
        next.tools().forEach(tool -> newNames.add(tool.exposedName()));
        Set<String> added = new HashSet<>(newNames);
        added.removeAll(oldNames);
        Set<String> removed = new HashSet<>(oldNames);
        removed.removeAll(newNames);
        if (!added.isEmpty() || !removed.isEmpty()) {
            LOG.info("mcp tool catalog changed: added={}, removed={}", added, removed);
        }
    }
}
