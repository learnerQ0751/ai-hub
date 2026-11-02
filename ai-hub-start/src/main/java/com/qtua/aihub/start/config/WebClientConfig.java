package com.qtua.aihub.start.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 客户端配置。
 *
 * <p>SSE 流式上游调用使用 WebClient；本项目仍是 Spring MVC，
 * 只引入 WebClient 客户端，不启用 WebFlux Server。</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * 提供 WebClient.Builder 供 integration 注入。
     *
     * @return WebClient 构建器
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
