package com.qtua.aihub.mcp;

import com.qtua.aihub.integration.mcp.StdioProcessRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2.10 stdio 孤儿进程清理测试。
 *
 * <p>模拟"子进程存活、父进程已死"的 PID 记录，验证启动清理逻辑能强杀并删除记录。</p>
 */
class StdioProcessRegistryOrphanTest {

    @Test
    void cleans_up_orphan_process_whose_parent_is_dead() throws Exception {
        StdioProcessRegistry registry = new StdioProcessRegistry();
        Process process = registry.start(new ProcessBuilder(List.of(
                javaExecutable(), "-cp", System.getProperty("java.class.path"),
                "com.qtua.aihub.mcp.InMemoryStdioServer")));
        try {
            assertThat(process.isAlive()).isTrue();
            Path pidFile = Path.of(System.getProperty("java.io.tmpdir"), "aihub-mcp-stdio",
                    process.pid() + ".pid");
            assertThat(Files.exists(pidFile)).isTrue();

            // 模拟上一代父进程已死：改写 parentPid 为不存在的 PID。
            Files.writeString(pidFile, process.pid() + "\n" + 999_999_999L + "\n",
                    StandardCharsets.UTF_8);
            registry.cleanupOrphans();

            assertThat(process.isAlive()).isFalse();
            assertThat(Files.exists(pidFile)).isFalse();
        } finally {
            registry.stop(process);
        }
    }

    @Test
    void stop_removes_pid_record_and_process() throws Exception {
        StdioProcessRegistry registry = new StdioProcessRegistry();
        Process process = registry.start(new ProcessBuilder(List.of(
                javaExecutable(), "-cp", System.getProperty("java.class.path"),
                "com.qtua.aihub.mcp.InMemoryStdioServer")));
        Path pidFile = Path.of(System.getProperty("java.io.tmpdir"), "aihub-mcp-stdio",
                process.pid() + ".pid");
        assertThat(Files.exists(pidFile)).isTrue();

        registry.stop(process);

        assertThat(process.isAlive()).isFalse();
        assertThat(Files.exists(pidFile)).isFalse();
    }

    private static String javaExecutable() {
        String home = System.getProperty("java.home");
        String exe = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return home + java.io.File.separator + "bin" + java.io.File.separator + exe;
    }
}
