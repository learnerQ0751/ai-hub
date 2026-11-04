package com.qtua.aihub.integration.mcp;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * MCP stdio 读取专用线程池（REQ-2.10）。
 *
 * <p>替代 ForkJoinPool.commonPool：阻塞在 {@code readLine} 的超时任务只占用本池，
 * 不会拖垮全局并行流；池有界，超时后由进程强杀释放。</p>
 */
@Component
public class McpIoExecutor implements DisposableBean {

    private static final int POOL_SIZE = 8;

    private final ExecutorService executor;

    /**
     * 构造有界守护线程池。
     */
    public McpIoExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "mcp-stdio-io");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(POOL_SIZE, factory);
    }

    /**
     * 在专用池中执行可能阻塞的读取任务。
     *
     * @param task 任务
     * @param <T>  结果类型
     * @return future
     */
    public <T> CompletableFuture<T> supply(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /**
     * 关闭线程池。
     */
    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
