package com.qtua.aihub.core.mcp;

import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.McpToolDescriptor;
import com.qtua.aihub.common.mcp.RawTool;
import com.qtua.aihub.common.mcp.ToolAggregationResult;
import com.qtua.aihub.integration.mcp.McpToolClient;
import com.qtua.aihub.manager.model.McpServerBO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * MCP 工具目录聚合器。
 *
 * <p>并行调用各 Server 的 {@code tools/list}，单 Server 失败只标记降级、不影响整体
 * （需求卡 2.2.3）。对外名统一为 {@code {serverName}__{toolName}}。</p>
 */
@Component
public class McpToolAggregator {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolAggregator.class);
    private static final int POOL_SIZE = 4;

    private final McpToolClient mcpToolClient;
    private final ExecutorService executor;

    /**
     * 构造聚合器。
     *
     * @param mcpToolClient 工具列表客户端
     */
    public McpToolAggregator(McpToolClient mcpToolClient) {
        this.mcpToolClient = mcpToolClient;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "mcp-tool-aggregator");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(POOL_SIZE, factory);
    }

    /**
     * 聚合工具目录。
     *
     * @param servers 已 ACTIVE 的 Server 列表
     * @return 聚合结果（含降级 Server）
     */
    public ToolAggregationResult aggregate(List<McpServerBO> servers) {
        if (Objects.isNull(servers) || servers.isEmpty()) {
            return new ToolAggregationResult(List.of(), List.of());
        }
        List<CompletableFuture<ServerTools>> futures = servers.stream()
                .map(server -> CompletableFuture.supplyAsync(() -> fetch(server), executor))
                .toList();
        List<McpToolDescriptor> tools = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        for (CompletableFuture<ServerTools> future : futures) {
            ServerTools result = future.join();
            if (Objects.nonNull(result.error())) {
                degraded.add(result.server().name());
                LOG.warn("mcp tool discovery degraded: server={}, error={}",
                        result.server().name(), result.error());
                continue;
            }
            for (RawTool raw : result.rawTools()) {
                tools.add(McpToolDescriptor.of(result.server().serverId(), result.server().name(),
                        result.server().tenantId(), raw.name(), raw.description(),
                        raw.inputSchema(), raw.idempotent()));
            }
        }
        return new ToolAggregationResult(tools, degraded);
    }

    private ServerTools fetch(McpServerBO server) {
        try {
            List<RawTool> rawTools = mcpToolClient.listTools(new McpProbeTarget(
                    server.name(), server.transport(), server.endpoint(),
                    server.command(), server.args()));
            return new ServerTools(server, rawTools, null);
        } catch (RuntimeException ex) {
            return new ServerTools(server, List.of(), ex.getMessage());
        }
    }

    /**
     * 关闭聚合线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private record ServerTools(McpServerBO server, List<RawTool> rawTools, String error) {
    }
}
