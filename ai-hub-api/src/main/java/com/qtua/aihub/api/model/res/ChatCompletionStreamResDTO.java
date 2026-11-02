package com.qtua.aihub.api.model.res;

import com.qtua.aihub.common.metering.TokenUsage;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式 ChatCompletion 响应契约。
 *
 * <p>events 为 SSE data 流；usage 在流结束后可读，供网关治理层结算。</p>
 */
public class ChatCompletionStreamResDTO {

    private final Flux<String> events;
    private final AtomicReference<TokenUsage> usage;
    private final String servedAlias;
    private final String deploymentModel;

    /**
     * 构造流式响应。
     *
     * @param events          SSE data 流
     * @param usage           流式 usage 引用
     * @param servedAlias     最终服务别名
     * @param deploymentModel 实际部署模型
     */
    public ChatCompletionStreamResDTO(Flux<String> events, AtomicReference<TokenUsage> usage,
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
