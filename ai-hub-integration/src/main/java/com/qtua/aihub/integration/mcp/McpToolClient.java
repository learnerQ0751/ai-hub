package com.qtua.aihub.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.RawTool;
import com.qtua.aihub.common.mcp.TransportType;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

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

/**
 * MCP 工具列表客户端（REQ-2.2）。
 *
 * <p>HTTP：POST JSON-RPC {@code tools/list}（兼容 JSON/SSE 响应）。
 * stdio：临时拉起子进程，完成 initialize → notifications/initialized → tools/list，
 * 读取响应后由进程注册表强杀（首版不复用连接）。</p>
 */
@Component
public class McpToolClient {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final String INITIALIZE_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,"
            + "\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
            + "\"capabilities\":{},\"clientInfo\":{\"name\":\"ai-hub\",\"version\":\"1.0.0\"}}}";
    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    private static final String TOOLS_LIST_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";

    private final ObjectMapper objectMapper;
    private final StdioProcessRegistry stdioProcessRegistry;
    private final WebClient httpClient;

    /**
     * 构造工具客户端。
     *
     * @param objectMapper          JSON 解析器
     * @param stdioProcessRegistry  stdio 子进程注册表
     */
    public McpToolClient(ObjectMapper objectMapper, StdioProcessRegistry stdioProcessRegistry) {
        this.objectMapper = objectMapper;
        this.stdioProcessRegistry = stdioProcessRegistry;
        this.httpClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                        .responseTimeout(TIMEOUT)))
                .build();
    }

    /**
     * 查询指定 Server 的工具列表。
     *
     * @param server 探活目标（连接信息）
     * @return 原始工具列表
     */
    public List<RawTool> listTools(McpProbeTarget server) {
        if (server.transport() == TransportType.HTTP) {
            return listToolsHttp(server);
        }
        if (server.transport() == TransportType.STDIO) {
            return listToolsStdio(server);
        }
        throw new IllegalStateException("传输方式 " + server.transport() + " 暂不支持工具发现");
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
            List<String> commandLine = new ArrayList<>();
            commandLine.add(server.command());
            commandLine.addAll(server.args());
            process = new ProcessBuilder(commandLine).start();
            stdioProcessRegistry.register(process);

            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            writer.write(INITIALIZE_REQUEST);
            writer.newLine();
            writer.flush();
            readLine(reader);
            writer.write(INITIALIZED_NOTIFICATION);
            writer.newLine();
            writer.flush();
            writer.write(TOOLS_LIST_REQUEST);
            writer.newLine();
            writer.flush();
            String response = readLine(reader);
            return parseTools(response);
        } catch (Exception ex) {
            LOG.warn("mcp stdio tools/list failed: server={}, error={}", server.name(), ex.getMessage());
            throw new IllegalStateException("tools/list 失败：" + ex.getMessage(), ex);
        } finally {
            if (Objects.nonNull(writer)) {
                try {
                    writer.close();
                } catch (java.io.IOException ignored) {
                    // 关闭管道失败不影响结果。
                }
            }
            if (Objects.nonNull(process)) {
                stdioProcessRegistry.stop(process);
            }
        }
    }

    private String readLine(BufferedReader reader) throws Exception {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                return reader.readLine();
            } catch (Exception ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        });
        return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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
                result.add(new RawTool(name, description,
                        Objects.isNull(schema) ? "{}" : schema.toString()));
            }
            return result;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("tools/list 响应无法解析：" + ex.getMessage(), ex);
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
}
