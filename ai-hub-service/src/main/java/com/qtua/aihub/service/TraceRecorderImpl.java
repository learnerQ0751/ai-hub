package com.qtua.aihub.service;

import com.qtua.aihub.common.trace.TraceEvent;
import com.qtua.aihub.common.trace.TraceRecorder;
import com.qtua.aihub.manager.trace.TraceEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Trace 事件异步记录实现（REQ-4.2）。
 *
 * <p>采样按 trace_id 确定性哈希，保证同一次请求的分段同留同弃；写入失败只打日志，
 * 不影响业务。执行器饱和时退回调用线程，宁可多花一点时间也不丢排障数据。</p>
 */
@Component
public class TraceRecorderImpl implements TraceRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(TraceRecorderImpl.class);
    private static final int SAMPLE_BUCKETS = 10_000;

    private final TraceEventManager traceEventManager;
    private final Executor executor;
    private final int sampleThreshold;

    /**
     * 构造记录器。
     *
     * @param traceEventManager Trace 数据门面
     * @param executor          异步执行器
     * @param sampleRate        采样率（0~1）
     */
    public TraceRecorderImpl(TraceEventManager traceEventManager,
                             @Qualifier("traceExecutor") Executor executor,
                             @Value("${gateway.trace.sample-rate:1.0}") double sampleRate) {
        this.traceEventManager = traceEventManager;
        this.executor = executor;
        double clamped = Math.max(0.0d, Math.min(1.0d, sampleRate));
        this.sampleThreshold = (int) Math.round(clamped * SAMPLE_BUCKETS);
    }

    @Override
    public void record(TraceEvent event) {
        if (ObjectUtils.isEmpty(event) || !sampled(event.traceId())) {
            return;
        }
        try {
            executor.execute(() -> insert(event));
        } catch (RejectedExecutionException ex) {
            insert(event);
        }
    }

    private boolean sampled(String traceId) {
        if (sampleThreshold >= SAMPLE_BUCKETS) {
            return true;
        }
        if (sampleThreshold <= 0) {
            return false;
        }
        return Math.floorMod(traceId.hashCode(), SAMPLE_BUCKETS) < sampleThreshold;
    }

    private void insert(TraceEvent event) {
        try {
            traceEventManager.append(event);
        } catch (RuntimeException ex) {
            LOG.error("failed to persist trace event traceId={} segment={}",
                    event.traceId(), event.segment(), ex);
        }
    }
}
