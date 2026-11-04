package com.qtua.aihub.integration.mcp;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * MCP HTTP 客户端工厂（REQ-2.10）。
 *
 * <p>显式管理连接池：上限、等待超时、空闲回收（短于常见服务端 keep-alive）、
 * 生命周期与后台清理；应用关闭时释放连接池。探活与工具调用共用配置但各自建
 * WebClient，避免探活异常影响业务连接。</p>
 */
@Component
public class McpHttpClientFactory implements DisposableBean {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int MAX_CONNECTIONS = 64;
    private static final Duration PENDING_ACQUIRE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MAX_IDLE_TIME = Duration.ofSeconds(30);
    private static final Duration MAX_LIFE_TIME = Duration.ofMinutes(5);
    private static final Duration EVICT_INTERVAL = Duration.ofSeconds(30);

    private final ConnectionProvider connectionProvider;

    /**
     * 构造连接池。
     */
    public McpHttpClientFactory() {
        this.connectionProvider = ConnectionProvider.builder("mcp-http")
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireTimeout(PENDING_ACQUIRE_TIMEOUT)
                // 短于常见服务端 keep-alive（Tomcat 60s），避免复用被对端关闭的连接。
                .maxIdleTime(MAX_IDLE_TIME)
                .maxLifeTime(MAX_LIFE_TIME)
                .evictInBackground(EVICT_INTERVAL)
                .build();
    }

    /**
     * 创建 MCP 客户端。
     *
     * @param responseTimeout 响应超时
     * @param maxInMemoryBytes 响应体缓冲上限
     * @return WebClient
     */
    public WebClient create(Duration responseTimeout, int maxInMemoryBytes) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                        .responseTimeout(responseTimeout)))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
                .build();
    }

    /**
     * 释放连接池。
     */
    @Override
    public void destroy() {
        connectionProvider.dispose();
    }
}
