package com.qtua.aihub.integration.mcp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * stdio MCP 子进程注册表（REQ-2.1 / 2.10）。
 *
 * <p>职责：
 * <ul>
 *   <li>统一启动子进程并登记，记录 {@code {pid, parentPid}} 到临时目录；</li>
 *   <li>正常停止时强杀并删除记录；</li>
 *   <li>应用启动时清理孤儿进程（子进程存活但父进程已死）——应对 JVM 崩溃
 *       （shutdown hook 不执行）导致的本机子进程残留；</li>
 *   <li>JVM 正常退出的 shutdown hook 兜底。</li>
 * </ul>
 * 约定：stdio MCP Server 必须支持 stdin EOF 退出（父进程消失时管道关闭）。
 * 服务器整机崩溃时子进程随机器消失，无需处理。</p>
 */
@Component
public class StdioProcessRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(StdioProcessRegistry.class);
    private static final Set<Process> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Path PID_DIR =
            Path.of(System.getProperty("java.io.tmpdir"), "aihub-mcp-stdio");

    static {
        // 兜底：JVM 正常退出（含 SIGTERM）时清理所有子进程。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process process : ACTIVE) {
                process.destroyForcibly();
            }
        }, "mcp-stdio-shutdown"));
    }

    /**
     * 启动并登记 stdio 子进程。
     *
     * @param builder 进程构建器
     * @return 子进程
     * @throws IOException 启动失败
     */
    public Process start(ProcessBuilder builder) throws IOException {
        Process process = builder.start();
        ACTIVE.add(process);
        writePidRecord(process);
        return process;
    }

    /**
     * 停止并移除子进程（幂等）。
     *
     * @param process 子进程
     */
    public void stop(Process process) {
        ACTIVE.remove(process);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        deletePidRecord(process);
    }

    /**
     * 当前本实例活跃子进程数（运行时统计用）。
     *
     * @return 数量
     */
    public int activeCount() {
        return ACTIVE.size();
    }

    /**
     * 启动时清理孤儿进程：子进程存活且父进程已不存在。
     */
    @PostConstruct
    public void cleanupOrphans() {
        if (!Files.isDirectory(PID_DIR)) {
            return;
        }
        try (Stream<Path> files = Files.list(PID_DIR)) {
            files.filter(path -> path.getFileName().toString().endsWith(".pid"))
                    .forEach(this::cleanupOrphan);
        } catch (IOException ex) {
            LOG.warn("failed to scan stdio pid records: dir={}", PID_DIR, ex);
        }
    }

    private void cleanupOrphan(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            String[] parts = content.split("\\r?\\n");
            long childPid = Long.parseLong(parts[0].trim());
            long parentPid = parts.length > 1 ? Long.parseLong(parts[1].trim()) : -1L;
            Optional<ProcessHandle> child = ProcessHandle.of(childPid);
            boolean parentAlive = parentPid > 0
                    && ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false);
            if (child.isPresent() && child.get().isAlive() && !parentAlive) {
                child.get().destroyForcibly();
                LOG.warn("killed orphan stdio mcp process: pid={}, deadParentPid={}",
                        childPid, parentPid);
            }
            Files.deleteIfExists(file);
        } catch (Exception ex) {
            LOG.warn("failed to clean stdio pid record: file={}, error={}",
                    file, ex.getMessage());
        }
    }

    private void writePidRecord(Process process) {
        try {
            Files.createDirectories(PID_DIR);
            String record = process.pid() + "\n" + ProcessHandle.current().pid() + "\n";
            Files.writeString(PID_DIR.resolve(process.pid() + ".pid"), record,
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            // 记录失败不影响启动；孤儿清理能力降级。
            LOG.warn("failed to write stdio pid record: pid={}", process.pid(), ex);
        }
    }

    private void deletePidRecord(Process process) {
        try {
            Files.deleteIfExists(PID_DIR.resolve(process.pid() + ".pid"));
        } catch (IOException ex) {
            LOG.warn("failed to delete stdio pid record: pid={}", process.pid(), ex);
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
            deletePidRecord(process);
        }
        ACTIVE.clear();
    }
}
