package com.qtua.aihub.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.TransportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP Server 探活器（REQ-2.1 / 2.10）。
 *
 * <p>两种传输：
 * <ul>
 *   <li>HTTP：JSON-RPC {@code initialize}，3 秒超时；探活使用 {@code Connection: close}
 *       短连接，不占用工具调用连接池；连接池由 {@link McpHttpClientFactory} 统一管理。</li>
 *   <li>stdio：启动本地子进程（经 {@link StdioProcessRegistry} 登记与孤儿保护）、
 *       写入单行 {@code initialize}、读取一行响应；读取使用 {@link McpIoExecutor}
 *       专用池，超时强杀。</li>
 * </ul></p>
 */
@Component
public class McpServerProbe {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerProbe.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int PROBE_MAX_IN_MEMORY_BYTES = 1024 * 1024;
    /**
     * initialize 请求必须是单行 JSON：stdio 传输按行分隔消息，多行文本会破坏协议帧。
     */
    private static final String INITIALIZE_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,"
            + "\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
            + "\"capabilities\":{},\"clientInfo\":{\"name\":\"ai-hub\",\"version\":\"1.0.0\"}}}";

    private final ObjectMapper objectMapper;
    private final StdioProcessRegistry stdioProcessRegistry;
    private final McpIoExecutor ioExecutor;
    private final WebClient httpClient;
    private final AtomicInteger inFlightHttp = new AtomicInteger();

    /**
     * 构造探活器。
     *
     * @param objectMapper         JSON 解析器
     * @param stdioProcessRegistry stdio 子进程注册表
     * @param ioExecutor           stdio 读取专用线程池
     * @param httpClientFactory    MCP HTTP 客户端工厂
     */
    public McpServerProbe(ObjectMapper objectMapper, StdioProcessRegistry stdioProcessRegistry,
                          McpIoExecutor ioExecutor, McpHttpClientFactory httpClientFactory) {
        this.objectMapper = objectMapper;
        this.stdioProcessRegistry = stdioProcessRegistry;
        this.ioExecutor = ioExecutor;
        this.httpClient = httpClientFactory.create(TIMEOUT, PROBE_MAX_IN_MEMORY_BYTES);
    }

    /**
     * 探活指定 Server。
     *
     * @param server MCP Server 业务对象
     * @return 探活结果
     */
    public ProbeResult probe(McpProbeTarget server) {
        if (server.transport() == TransportType.HTTP) {
            return probeHttp(server);
        }
        if (server.transport() == TransportType.STDIO) {
            return probeStdio(server);
        }
        return ProbeResult.fail("传输方式 " + server.transport() + " 暂不支持探活");
    }

    /**
     * 进行中的 HTTP 探活数（运行时统计）。
     *
     * @return 数量
     */
    public int inFlightHttpCount() {
        return inFlightHttp.get();
    }

    private ProbeResult probeHttp(McpProbeTarget server) {
        if (!StringUtils.hasText(server.endpoint())) {
            return ProbeResult.fail("HTTP 传输缺少 endpoint");
        }
        inFlightHttp.incrementAndGet();
        try {
            String body = httpClient.post()
                    .uri(server.endpoint())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                    // 探活短连接：不占用/复用业务连接池。
                    .header(HttpHeaders.CONNECTION, "close")
                    .bodyValue(INITIALIZE_REQUEST)
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty(""))
                    .block(TIMEOUT);
            return evaluate(body);
        } catch (RuntimeException ex) {
            LOG.warn("mcp http probe failed: server={}, error={}",
                    server.name(), ex.getMessage());
            return ProbeResult.fail("探活失败：" + rootMessage(ex));
        } finally {
            inFlightHttp.decrementAndGet();
        }
    }

    private ProbeResult probeStdio(McpProbeTarget server) {
        if (!StringUtils.hasText(server.command())) {
            return ProbeResult.fail("stdio 传输缺少 command");
        }
        Process process = null;
        BufferedWriter writer = null;
        try {
            List<String> commandLine = new ArrayList<>();
            commandLine.add(server.command());
            commandLine.addAll(server.args());
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.redirectErrorStream(false);
            process = stdioProcessRegistry.start(builder);

            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            writer.write(INITIALIZE_REQUEST);
            writer.newLine();
            writer.flush();

            Process finalProcess = process;
            CompletableFuture<String> reader = ioExecutor.supply(() -> {
                try (BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(finalProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    return bufferedReader.readLine();
                }
            });
            String response = reader.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return evaluate(response);
        } catch (java.util.concurrent.TimeoutException ex) {
            return ProbeResult.fail("stdio 探活超时（" + TIMEOUT.toSeconds() + " 秒）");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ProbeResult.fail("stdio 探活被中断");
        } catch (Exception ex) {
            LOG.warn("mcp stdio probe failed: server={}, error={}", server.name(), ex.getMessage());
            return ProbeResult.fail("探活失败：" + rootMessage(ex));
        } finally {
            if (Objects.nonNull(writer)) {
                try {
                    writer.close();
                } catch (java.io.IOException ignored) {
                    // 探活结束关闭管道；失败不影响结果。
                }
            }
            if (Objects.nonNull(process)) {
                stdioProcessRegistry.stop(process);
            }
        }
    }

    /**
     * 解析 initialize 响应：支持纯 JSON 与 SSE（取第一条 data 行）。
     *
     * @param body 响应体
     * @return 探活结果
     */
    private ProbeResult evaluate(String body) {
        if (!StringUtils.hasText(body)) {
            return ProbeResult.fail("探活失败：上游返回空响应");
        }
        try {
            String json = extractJson(body);
            JsonNode node = objectMapper.readTree(json);
            JsonNode error = node.get("error");
            if (Objects.nonNull(error) && !error.isNull()) {
                return ProbeResult.fail("initialize 失败：" + error.path("message").asText());
            }
            JsonNode result = node.get("result");
            if (Objects.isNull(result) || result.isNull()) {
                return ProbeResult.fail("initialize 响应缺少 result");
            }
            return ProbeResult.ok();
        } catch (Exception ex) {
            return ProbeResult.fail("initialize 响应无法解析：" + rootMessage(ex));
        }
    }

    private String extractJson(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("event:") || trimmed.startsWith("data:")) {
            for (String line : trimmed.split("\\r?\\n")) {
                if (line.startsWith("data:")) {
                    return line.substring("data:".length()).trim();
                }
            }
        }
        return trimmed;
    }

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (Objects.nonNull(current.getCause())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.hasText(message) ? message : current.getClass().getSimpleName();
    }
}
