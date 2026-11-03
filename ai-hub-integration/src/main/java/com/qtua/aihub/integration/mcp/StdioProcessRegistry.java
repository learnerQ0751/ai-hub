package com.qtua.aihub.integration.mcp;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * stdio MCP 子进程注册表。
 *
 * <p>维护当前实例启动的 stdio 子进程，保证探活超时、应用关闭、JVM 退出时都能
 * 强制清理，避免子进程泄漏（REQ-2.1 常见坑）。</p>
 */
@Component
public class StdioProcessRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(StdioProcessRegistry.class);
    private static final Set<Process> ACTIVE = ConcurrentHashMap.newKeySet();

    static {
        // 兜底：JVM 退出（含非正常退出）时清理所有子进程。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process process : ACTIVE) {
                process.destroyForcibly();
            }
        }, "mcp-stdio-shutdown"));
    }

    /**
     * 登记一个刚启动的 stdio 进程。
     *
     * @param process 子进程
     */
    public void register(Process process) {
        ACTIVE.add(process);
    }

    /**
     * 停止并移除一个 stdio 进程（幂等）。
     *
     * @param process 子进程
     */
    public void stop(Process process) {
        ACTIVE.remove(process);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * 应用关闭时清理全部子进程。
     */
    @PreDestroy
    public void shutdown() {
        LOG.info("stopping {} stdio mcp process(es)", ACTIVE.size());
        for (Process process : ACTIVE) {
            process.destroyForcibly();
        }
        ACTIVE.clear();
    }
}
