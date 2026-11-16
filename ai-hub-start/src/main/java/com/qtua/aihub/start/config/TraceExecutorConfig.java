package com.qtua.aihub.start.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trace 异步写入线程池（REQ-4.2）。
 *
 * <p>有界队列 + 显式参数（STANDARDS 线程池规则）：2 核 4 峰值，队列 2000，
 * 饱和由调用方兜底同步写入；线程名便于排障。</p>
 */
@Configuration
public class TraceExecutorConfig {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 2000;

    /**
     * Trace 写入执行器。
     *
     * @return 有界线程池
     */
    @Bean(name = "traceExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor traceExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "trace-writer-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
