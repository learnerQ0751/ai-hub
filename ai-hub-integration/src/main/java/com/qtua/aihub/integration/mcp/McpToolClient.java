package com.qtua.aihub.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.RawTool;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeoutException;

/**
 * MCP 工具客户端（REQ-2.2 工具发现 + REQ-2.3 调用代理）。
 *
 * <p>HTTP：POST JSON-RPC（兼容 JSON/SSE 响应）。
 * stdio：临时拉起子进程，完成 initialize → notifications/initialized → 目标方法，
 * 读取响应后由进程注册表强杀（首版不复用连接）。</p>
 */
@Component
public class McpToolClient {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    /** 工具结果可能很大（>1MB 需截断），提高 WebClient 缓冲上限。 */
    private static final int MAX_IN_MEMORY_BYTES = 8 * 1024 * 1024;
    private static final String INITIALIZE_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,"
            + "\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
            + "\"capabilities\":{},\"clientInfo\":{\"name\":\"ai-hub\",\"version\":\"1.0.0\"}}}";
    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    private static final String TOOLS_LIST_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";

    private final ObjectMapper objectMapper;
    private final StdioProcessRegistry stdioProcessRegistry;
    private final McpIoExecutor ioExecutor;
    private final WebClient httpClient;
    private final AtomicInteger inFlight = new AtomicInteger();

    /**
     * 构造工具客户端。
     *
     * @param objectMapper         JSON 解析器
     * @param stdioProcessRegistry stdio 子进程注册表
     * @param ioExecutor           stdio 读取专用线程池
     * @param httpClientFactory    MCP HTTP 客户端工厂（显式连接池）
     */
    public McpToolClient(ObjectMapper objectMapper, StdioProcessRegistry stdioProcessRegistry,
                         McpIoExecutor ioExecutor, McpHttpClientFactory httpClientFactory) {
        this.objectMapper = objectMapper;
        this.stdioProcessRegistry = stdioProcessRegistry;
        this.ioExecutor = ioExecutor;
        this.httpClient = httpClientFactory.create(TIMEOUT, MAX_IN_MEMORY_BYTES);
    }

    /**
     * 调用指定工具（JSON-RPC tools/call），原样返回 result JSON。
     *
     * @param server        探活目标（连接信息）
     * @param toolName      上游原始工具名
     * @param argumentsJson 参数 JSON 文本（必须为对象）
     * @return 上游 result 的 JSON 文本
     */
    public String callTool(McpProbeTarget server, String toolName, String argumentsJson) {
        inFlight.incrementAndGet();
        try {
            String request = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":" + quote(toolName) + ",\"arguments\":"
                    + argumentsJson + "}}";
            if (server.transport() == TransportType.HTTP) {
                return callToolHttp(server, request);
            }
            if (server.transport() == TransportType.STDIO) {
                return callToolStdio(server, request);
            }
            throw new IllegalStateException("传输方式 " + server.transport() + " 暂不支持工具调用");
        } finally {
            inFlight.decrementAndGet();
        }
    }

    /**
     * 进行中的工具调用数（运行时统计）。
     *
     * @return 数量
     */
    public int inFlightCount() {
        return inFlight.get();
    }

    /**
     * 查询指定 Server 的工具列表。
     *
     * @param server 探活目标（连接信息）
     * @return 原始工具列表
     */
    public List<RawTool> listTools(McpProbeTarget server) {
        inFlight.incrementAndGet();
        try {
            if (server.transport() == TransportType.HTTP) {
                return listToolsHttp(server);
            }
            if (server.transport() == TransportType.STDIO) {
                return listToolsStdio(server);
            }
            throw new IllegalStateException("传输方式 " + server.transport() + " 暂不支持工具发现");
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private String callToolHttp(McpProbeTarget server, String request) {
        String body;
        try {
            body = httpClient.post()
                    .uri(server.endpoint())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                    .bodyValue(request)
                    .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                    .block(TIMEOUT);
        } catch (IllegalStateException ex) {
            if (isTimeout(ex)) {
                throw new McpToolTimeoutException("HTTP 工具调用超时", ex);
            }
            throw ex;
        }
        return parseCallResult(body);
    }

    private String callToolStdio(McpProbeTarget server, String callRequest) {
        Process process = null;
        BufferedWriter writer = null;
        try {
            Process[] holder = startSession(server);
            process = holder[0];
            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            handshake(writer, reader);
            writer.write(callRequest);
            writer.newLine();
            writer.flush();
            return parseCallResult(readLine(reader));
        } catch (McpToolTimeoutException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warn("mcp stdio tools/call failed: server={}, error={}",
                    server.name(), ex.getMessage());
            throw new IllegalStateException("tools/call 失败：" + ex.getMessage(), ex);
        } finally {
            closeQuietly(writer);
            if (Objects.nonNull(process)) {
                stdioProcessRegistry.stop(process);
            }
        }
    }

    private List<RawTool> listToolsHttp(McpProbeTarget server) {
        String body = httpClient.post()
                .uri(server.endpoint())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .bodyValue(TOOLS_LIST_REQUEST)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                .block(TIMEOUT);
        return parseTools(body);
    }

    private List<RawTool> listToolsStdio(McpProbeTarget server) {
        Process process = null;
        BufferedWriter writer = null;
        try {
            Process[] holder = startSession(server);
            process = holder[0];
            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            handshake(writer, reader);
            writer.write(TOOLS_LIST_REQUEST);
            writer.newLine();
            writer.flush();
            return parseTools(readLine(reader));
        } catch (Exception ex) {
            LOG.warn("mcp stdio tools/list failed: server={}, error={}",
                    server.name(), ex.getMessage());
            throw new IllegalStateException("tools/list 失败：" + ex.getMessage(), ex);
        } finally {
            closeQuietly(writer);
            if (Objects.nonNull(process)) {
                stdioProcessRegistry.stop(process);
            }
        }
    }

    /**
     * 启动 stdio 子进程并登记（返回数组以规避 finally 可见性问题）。
     */
    private Process[] startSession(McpProbeTarget server) throws Exception {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(server.command());
        commandLine.addAll(server.args());
        Process process = stdioProcessRegistry.start(new ProcessBuilder(commandLine));
        return new Process[]{process};
    }

    private void handshake(BufferedWriter writer, BufferedReader reader) throws Exception {
        writer.write(INITIALIZE_REQUEST);
        writer.newLine();
        writer.flush();
        readLine(reader);
        writer.write(INITIALIZED_NOTIFICATION);
        writer.newLine();
        writer.flush();
    }

    private String readLine(BufferedReader reader) throws Exception {
        CompletableFuture<String> future = ioExecutor.supply(reader::readLine);
        try {
            return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new McpToolTimeoutException("stdio 响应超时", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("stdio 读取失败", ex.getCause());
        }
    }

    /**
     * 解析 tools/list 响应（JSON 或 SSE）。
     *
     * @param body 响应体
     * @return 原始工具列表
     */
    private List<RawTool> parseTools(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("tools/list 返回空响应");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(body));
            JsonNode error = node.get("error");
            if (Objects.nonNull(error) && !error.isNull()) {
                throw new IllegalStateException(error.path("message").asText());
            }
            JsonNode tools = node.path("result").path("tools");
            if (tools.isMissingNode() || !tools.isArray()) {
                throw new IllegalStateException("tools/list 响应缺少 result.tools");
            }
            List<RawTool> result = new ArrayList<>();
            for (JsonNode tool : tools) {
                String name = tool.path("name").asText(null);
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String description = tool.path("description").asText(null);
                JsonNode schema = tool.get("inputSchema");
                boolean idempotent = tool.path("_meta").path("idempotent").asBoolean(false);
                result.add(new RawTool(name, description,
                        Objects.isNull(schema) ? "{}" : schema.toString(), idempotent));
            }
            return result;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("tools/list 响应无法解析：" + ex.getMessage(), ex);
        }
    }

    /**
     * 解析 tools/call 响应，返回上游 result 的 JSON 文本。
     *
     * @param body 响应体
     * @return result JSON
     */
    private String parseCallResult(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("tools/call 返回空响应");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(body));
            JsonNode error = node.get("error");
            if (Objects.nonNull(error) && !error.isNull()) {
                throw new IllegalStateException(error.path("message").asText());
            }
            JsonNode result = node.get("result");
            if (Objects.isNull(result) || result.isNull()) {
                throw new IllegalStateException("tools/call 响应缺少 result");
            }
            return result.toString();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("tools/call 响应无法解析：" + ex.getMessage(), ex);
        }
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (Objects.nonNull(current)) {
            if (current instanceof TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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

    private void closeQuietly(BufferedWriter writer) {
        if (Objects.nonNull(writer)) {
            try {
                writer.close();
            } catch (java.io.IOException ignored) {
                // 关闭管道失败不影响结果。
            }
        }
    }
}
