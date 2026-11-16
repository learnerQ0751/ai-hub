package com.qtua.aihub.common.trace;

/**
 * Trace 事件记录端口（REQ-4.2）。
 *
 * <p>业务只声明「发生了什么」，异步落库、采样与异常吞没由实现负责；
 * 未接入实现时使用 {@link #NOOP}，不阻塞业务。</p>
 */
public interface TraceRecorder {

    /** 空实现（单测/禁用 Trace）。 */
    TraceRecorder NOOP = event -> {
    };

    /**
     * 记录一条分段事件。
     *
     * @param event 事件
     */
    void record(TraceEvent event);
}
