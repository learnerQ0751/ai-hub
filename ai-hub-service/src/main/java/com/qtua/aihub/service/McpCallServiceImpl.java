package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.api.McpCallService;
import com.qtua.aihub.api.McpToolService;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.McpToolDescriptor;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.integration.mcp.McpToolClient;
import com.qtua.aihub.integration.mcp.McpToolTimeoutException;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.model.McpServerBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * MCP 工具调用代理实现（REQ-2.3）。
 *
 * <p>路由：对外工具名 → 工具目录 → Server → 上游 {@code tools/call}。
 * 错误归一：工具/Server 不存在 404、超时 504、参数错误 400、其他上游失败 502；
 * 大结果（>1MB）截断并记录原始大小；仅幂等工具允许失败重试一次。</p>
 */
@Service
public class McpCallServiceImpl implements McpCallService {

    private static final Logger LOG = LoggerFactory.getLogger(McpCallServiceImpl.class);
    private static final int MAX_RESULT_BYTES = 1024 * 1024;
    /** 截断时按 4 倍保守估计保留字符数（UTF-8 单字符最多 4 字节）。 */
    private static final int TRUNCATE_CHAR_DIVISOR = 4;

    private final McpToolService mcpToolService;
    private final McpServerManager mcpServerManager;
    private final McpToolClient mcpToolClient;
    private final ObjectMapper objectMapper;
    private final TenantScopeGuard tenantScopeGuard;

    /**
     * 构造服务。
     *
     * @param mcpToolService   工具目录服务
     * @param mcpServerManager Server 数据门面
     * @param mcpToolClient    工具调用客户端
     * @param objectMapper     JSON 解析器
     */
    public McpCallServiceImpl(McpToolService mcpToolService, McpServerManager mcpServerManager,
                              McpToolClient mcpToolClient, ObjectMapper objectMapper) {
        this(mcpToolService, mcpServerManager, mcpToolClient, objectMapper,
                new TenantScopeGuard());
    }

    /**
     * 构造服务（租户隔离）。
     *
     * @param mcpToolService   工具目录服务
     * @param mcpServerManager Server 数据门面
     * @param mcpToolClient    工具调用客户端
     * @param objectMapper     JSON 解析器
     * @param tenantScopeGuard 租户作用域守卫
     */
    @org.springframework.beans.factory.annotation.Autowired
    public McpCallServiceImpl(McpToolService mcpToolService, McpServerManager mcpServerManager,
                              McpToolClient mcpToolClient, ObjectMapper objectMapper,
                              TenantScopeGuard tenantScopeGuard) {
        this.mcpToolService = mcpToolService;
        this.mcpServerManager = mcpServerManager;
        this.mcpToolClient = mcpToolClient;
        this.objectMapper = objectMapper;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    /**
     * 调用工具。
     *
     * @param toolName    对外工具名
     * @param requestBody 请求体 JSON
     * @return 上游 result JSON
     */
    @Override
    public String call(String toolName, String requestBody) {
        String argumentsJson = extractArguments(requestBody);
        McpToolDescriptor tool = mcpToolService.find(toolName)
                .orElseThrow(() -> ChatCompletionException.mcpToolNotFound(toolName));
        McpServerBO server = mcpServerManager.findByServerId(tool.serverId())
                .orElseThrow(() -> ChatCompletionException.mcpToolNotFound(toolName));
        assertServerTenant(server);
        if (server.status() != McpServerStatus.ACTIVE) {
            throw ChatCompletionException.upstreamError(
                    "MCP Server '" + server.name() + "' 当前不可用", null);
        }
        McpProbeTarget target = new McpProbeTarget(server.name(), server.transport(),
                server.endpoint(), server.command(), server.args());
        String result = invokeWithRetry(tool, target, argumentsJson);
        return truncateIfNeeded(toolName, result);
    }

    /**
     * 校验工具所属 Server 与调用方租户一致（master 可调用全部）。
     *
     * @param server 工具所属 Server
     */
    private void assertServerTenant(McpServerBO server) {
        TenantIdentity identity = tenantScopeGuard.currentIdentity();
        if (!ObjectUtils.isEmpty(identity) && identity.master()) {
            return;
        }
        String tenantId = tenantScopeGuard.resolveTenant(null);
        if (!ObjectUtils.isEmpty(server.tenantId()) && !tenantId.equals(server.tenantId())) {
            LOG.warn("mcp cross-tenant call denied: keyHash={}, server={}, tenant={}",
                    ObjectUtils.isEmpty(identity) ? null : identity.keyHash(), server.name(), server.tenantId());
            throw ChatCompletionException.mcpToolNotFound(null);
        }
    }

    /**
     * 调用（幂等工具失败重试一次）。
     *
     * @param tool          工具描述
     * @param target        连接信息
     * @param argumentsJson 参数 JSON
     * @return result JSON
     */
    private String invokeWithRetry(McpToolDescriptor tool, McpProbeTarget target,
                                   String argumentsJson) {
        try {
            return mcpToolClient.callTool(target, tool.toolName(), argumentsJson);
        } catch (RuntimeException first) {
            if (!tool.idempotent()) {
                throw mapError(first);
            }
            LOG.warn("mcp tool call failed, retrying once (idempotent): tool={}, error={}",
                    tool.exposedName(), first.getMessage());
            try {
                return mcpToolClient.callTool(target, tool.toolName(), argumentsJson);
            } catch (RuntimeException second) {
                throw mapError(second);
            }
        }
    }

    private ChatCompletionException mapError(RuntimeException ex) {
        if (ex instanceof McpToolTimeoutException) {
            return ChatCompletionException.upstreamTimeout(ex);
        }
        return ChatCompletionException.upstreamError("MCP 工具调用失败", ex);
    }

    /**
     * 解析 arguments：缺省为 {}；非对象报 400。
     *
     * @param requestBody 请求体
     * @return arguments JSON
     */
    private String extractArguments(String requestBody) {
        if (!StringUtils.hasText(requestBody)) {
            return "{}";
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(requestBody);
        } catch (Exception ex) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "请求体不是合法的 JSON", "body");
        }
        if (!root.isObject()) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "请求体必须是 JSON 对象", "body");
        }
        JsonNode arguments = root.get("arguments");
        if (Objects.isNull(arguments) || arguments.isNull()) {
            return "{}";
        }
        if (!arguments.isObject()) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "arguments 必须是 JSON 对象", "arguments");
        }
        return arguments.toString();
    }

    /**
     * 大结果截断（>1MB），保留原始大小。
     *
     * @param toolName 工具名（日志）
     * @param result   result JSON
     * @return 原结果或截断包装
     */
    private String truncateIfNeeded(String toolName, String result) {
        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_RESULT_BYTES) {
            return result;
        }
        LOG.warn("mcp tool result truncated: tool={}, originalSize={} bytes",
                toolName, bytes.length);
        int keepChars = Math.min(result.length(), MAX_RESULT_BYTES / TRUNCATE_CHAR_DIVISOR);
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.putArray("content").addObject()
                .put("type", "text")
                .put("text", result.substring(0, keepChars));
        wrapper.put("isError", false);
        wrapper.put("truncated", true);
        wrapper.put("originalSize", bytes.length);
        return wrapper.toString();
    }
}
