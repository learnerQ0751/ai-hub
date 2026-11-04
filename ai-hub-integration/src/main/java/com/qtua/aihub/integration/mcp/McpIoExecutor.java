package com.qtua.aihub.integration.mcp;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * MCP stdio 读取专用线程池（REQ-2.10）。
 *
 * <p>替代 ForkJoinPool.commonPool：阻塞在 {@code readLine} 的超时任务只占用本池，
 * 不会拖垮全局并行流；池有界，超时后由进程强杀释放。</p>
 */
@Component
public class McpIoExecutor implements DisposableBean {

    private static final int CORE_POOL_SIZE = 8;
    private static final int MAX_POOL_SIZE = 8;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 100;

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
        // 显式参数 + 有界队列 + CallerRuns：禁止 Executors 工厂（rule-java-threadpool-001）。
        this.executor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
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
