package com.qtua.aihub.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 测试用 stdio MCP Server：逐行读取 JSON-RPC（严格 JSON 解析，模拟真实 Server），
 * 实现 initialize / tools/list / tools/call。
 *
 * <p>以独立 JVM 进程启动（{@code java -cp ... InMemoryStdioServer}），
 * 供 REQ-2.1 stdio 探活与后续工具调用测试复用。严格解析可暴露
 * "多行 JSON 破坏协议帧" 一类问题。</p>
 */
public final class InMemoryStdioServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemoryStdioServer() {
    }

    /**
     * 进程入口：读 stdin、写 stdout，直到 EOF。
     *
     * @param args 忽略
     * @throws Exception IO 异常
     */
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String response = handle(line);
            if (response != null) {
                writer.println(response);
            }
        }
    }

    private static String handle(String line) {
        JsonNode request;
        try {
            request = MAPPER.readTree(line);
        } catch (Exception ex) {
            System.err.println("[InMemoryStdioServer] invalid JSON line: " + ex.getMessage());
            return null;
        }
        JsonNode id = request.get("id");
        String method = request.path("method").asText(null);
        if (method == null) {
            return null;
        }
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        switch (method) {
            case "initialize" -> {
                ObjectNode result = MAPPER.createObjectNode();
                result.put("protocolVersion", "2025-06-18");
                result.set("capabilities", MAPPER.createObjectNode());
                ObjectNode serverInfo = MAPPER.createObjectNode();
                serverInfo.put("name", "in-memory-stdio");
                serverInfo.put("version", "1.0.0");
                result.set("serverInfo", serverInfo);
                response.set("result", result);
            }
            case "notifications/initialized" -> {
                return null;
            }
            case "tools/list" -> {
                ObjectNode tool = MAPPER.createObjectNode();
                tool.put("name", "ping");
                tool.put("description", "Ping the stdio server");
                ObjectNode schema = MAPPER.createObjectNode();
                schema.put("type", "object");
                schema.set("properties", MAPPER.createObjectNode());
                tool.set("inputSchema", schema);
                ObjectNode result = MAPPER.createObjectNode();
                result.set("tools", MAPPER.createArrayNode().add(tool));
                response.set("result", result);
            }
            case "tools/call" -> {
                ObjectNode result = MAPPER.createObjectNode();
                ObjectNode content = MAPPER.createObjectNode();
                content.put("type", "text");
                content.put("text", "pong");
                result.set("content", MAPPER.createArrayNode().add(content));
                result.put("isError", false);
                response.set("result", result);
            }
            default -> {
                ObjectNode error = MAPPER.createObjectNode();
                error.put("code", -32601);
                error.put("message", "Method not found");
                response.set("error", error);
            }
        }
        return response.toString();
    }
}
