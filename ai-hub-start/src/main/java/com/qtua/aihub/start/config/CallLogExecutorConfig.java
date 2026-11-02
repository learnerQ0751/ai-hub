package com.qtua.aihub.start.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 调用日志异步执行器配置。
 *
 * <p>有界队列 + CallerRunsPolicy：队列满时由请求线程执行写库，
 * 保证审计日志不丢；线程前缀便于日志排查。</p>
 */
@Configuration
public class CallLogExecutorConfig {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 1000;

    /**
     * 调用日志写入执行器。
     *
     * @return 有界线程池
     */
    @Bean(name = "callLogExecutor")
    public ThreadPoolTaskExecutor callLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("call-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
