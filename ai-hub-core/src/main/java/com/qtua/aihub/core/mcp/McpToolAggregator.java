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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP 工具目录聚合器。
 *
 * <p>并行调用各 Server 的 {@code tools/list}，单 Server 失败只标记降级、不影响整体
 * （需求卡 2.2.3）。对外名统一为 {@code {serverName}__{toolName}}。</p>
 */
@Component
public class McpToolAggregator {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolAggregator.class);
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 4;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 64;
    /**
     * 单次聚合的兜底等待上限：任务内部已有 3s 超时，这里防止底层异常卡死拖住整个目录刷新。
     */
    private static final Duration AGGREGATE_TIMEOUT = Duration.ofSeconds(5);

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
        // 显式参数 + 有界队列 + CallerRuns：禁止 Executors 工厂（rule-java-threadpool-001）。
        this.executor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
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
        // 先全部提交（并行执行），再按提交顺序收集；总耗时由最慢任务决定，不是串行累加。
        List<PendingTask> pending = servers.stream()
                .map(server -> new PendingTask(server,
                        CompletableFuture.supplyAsync(() -> fetch(server), executor)))
                .toList();
        List<McpToolDescriptor> tools = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        for (PendingTask task : pending) {
            ServerTools result;
            try {
                result = task.future().get(AGGREGATE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                task.future().cancel(true);
                degraded.add(task.server().name());
                LOG.warn("mcp tool discovery timeout: server={}", task.server().name());
                continue;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                degraded.add(task.server().name());
                break;
            } catch (ExecutionException ex) {
                degraded.add(task.server().name());
                LOG.warn("mcp tool discovery failed: server={}, error={}",
                        task.server().name(), rootMessage(ex));
                continue;
            }
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

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (Objects.nonNull(current.getCause())) {
            current = current.getCause();
        }
        return Objects.nonNull(current.getMessage())
                ? current.getMessage() : current.getClass().getSimpleName();
    }

    private record PendingTask(McpServerBO server, CompletableFuture<ServerTools> future) {
    }

    private record ServerTools(McpServerBO server, List<RawTool> rawTools, String error) {
    }
}
