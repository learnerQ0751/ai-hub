package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.metering.TokenUsage;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式聊天补全结果业务对象。
 *
 * <p>events 为已改写 model 的 SSE data 流；usage 在流处理过程中由 core 更新，
 * 流结束后由调用方读取用于计量与预算结算。该类只承载单次请求的流上下文，
 * 不跨请求共享。</p>
 */
public class ChatCompletionStreamResultBO {

    private final Flux<String> events;
    private final AtomicReference<TokenUsage> usage;
    private final String servedAlias;
    private final String deploymentModel;

    /**
     * 构造流式结果。
     *
     * @param events          SSE data 流
     * @param usage           流式 usage 引用（末块更新）
     * @param servedAlias     最终服务别名
     * @param deploymentModel 实际部署模型
     */
    public ChatCompletionStreamResultBO(Flux<String> events, AtomicReference<TokenUsage> usage,
                                        String servedAlias, String deploymentModel) {
        this.events = events;
        this.usage = usage;
        this.servedAlias = servedAlias;
        this.deploymentModel = deploymentModel;
    }

    public Flux<String> getEvents() {
        return events;
    }

    public AtomicReference<TokenUsage> getUsage() {
        return usage;
    }

    public String getServedAlias() {
        return servedAlias;
    }

    public String getDeploymentModel() {
        return deploymentModel;
    }
}
