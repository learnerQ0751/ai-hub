package com.qtua.aihub.service;

import com.qtua.aihub.api.McpServerService;
import com.qtua.aihub.api.model.req.McpServerRegisterReqDTO;
import com.qtua.aihub.api.model.res.McpServerResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.TransportType;
import com.qtua.aihub.common.util.UrlMasker;
import com.qtua.aihub.integration.mcp.McpServerProbe;
import com.qtua.aihub.integration.mcp.ProbeResult;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.model.McpServerBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * MCP Server 注册与发现实现。
 *
 * <p>职责：参数校验、查重、探活编排（注册即探活）、BO↔DTO 转换与 endpoint 掩码。
 * 数据编解码在 manager；HTTP/stdio 探活在 integration。</p>
 */
@Service
public class McpServerServiceImpl implements McpServerService {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerServiceImpl.class);
    private static final int NAME_MAX_LENGTH = 128;
    private static final int ENDPOINT_MAX_LENGTH = 512;
    private static final String TOOL_SEPARATOR = "__";

    private final McpServerManager mcpServerManager;
    private final McpServerProbe mcpServerProbe;

    /**
     * 构造服务。
     *
     * @param mcpServerManager MCP Server 数据门面
     * @param mcpServerProbe   探活器
     */
    public McpServerServiceImpl(McpServerManager mcpServerManager, McpServerProbe mcpServerProbe) {
        this.mcpServerManager = mcpServerManager;
        this.mcpServerProbe = mcpServerProbe;
    }

    /**
     * 注册 MCP Server。
     *
     * @param reqDTO 注册参数
     * @return 注册结果（含探活后的状态）
     */
    @Override
    public McpServerResDTO register(McpServerRegisterReqDTO reqDTO) {
        String name = validateName(reqDTO.name());
        TransportType transport = validateTransport(reqDTO.transport());
        String endpoint = normalizeEndpoint(transport, reqDTO.endpoint());
        String command = validateCommand(transport, reqDTO.command());

        if (mcpServerManager.findByName(name).isPresent()) {
            throw ChatCompletionException.mcpServerConflict(name);
        }

        McpServerBO server = new McpServerBO(
                UUID.randomUUID().toString(),
                name,
                transport,
                endpoint,
                command,
                reqDTO.args(),
                reqDTO.tenantId(),
                McpServerStatus.DOWN,
                null,
                reqDTO.description(),
                null,
                Instant.now());
        try {
            mcpServerManager.save(server);
        } catch (DuplicateKeyException ex) {
            // 并发注册同名：DB 唯一约束兜底。
            throw ChatCompletionException.mcpServerConflict(name);
        }
        LOG.info("mcp server registered: name={}, transport={}, serverId={}",
                name, transport.value(), server.serverId());
        return toResDTO(probeAndUpdate(server));
    }

    /**
     * 查询全部 MCP Server。
     *
     * @return 服务器列表（endpoint 已掩码）
     */
    @Override
    public List<McpServerResDTO> list() {
        return mcpServerManager.findAll().stream().map(this::toResDTO).toList();
    }

    /**
     * 注销 MCP Server。
     *
     * @param serverId 对外服务器 ID
     */
    @Override
    public void remove(String serverId) {
        McpServerBO server = mcpServerManager.findByServerId(serverId)
                .orElseThrow(() -> ChatCompletionException.mcpServerNotFound(serverId));
        if (!mcpServerManager.delete(server.serverId())) {
            throw ChatCompletionException.mcpServerNotFound(serverId);
        }
        LOG.info("mcp server removed: name={}, serverId={}", server.name(), server.serverId());
    }

    /**
     * 手动重新探活。
     *
     * @param serverId 对外服务器 ID
     * @return 探活后的最新视图
     */
    @Override
    public McpServerResDTO reprobe(String serverId) {
        McpServerBO server = mcpServerManager.findByServerId(serverId)
                .orElseThrow(() -> ChatCompletionException.mcpServerNotFound(serverId));
        return toResDTO(probeAndUpdate(server));
    }

    /**
     * 探活并刷新状态。
     *
     * @param server 业务对象
     * @return 刷新后的业务对象
     */
    private McpServerBO probeAndUpdate(McpServerBO server) {
        ProbeResult result = mcpServerProbe.probe(
                new McpProbeTarget(server.name(), server.transport(), server.endpoint(),
                        server.command(), server.args()));
        McpServerStatus status = result.healthy() ? McpServerStatus.ACTIVE : McpServerStatus.DOWN;
        McpServerBO updated = server.withHealth(status, Instant.now(), result.error());
        mcpServerManager.updateHealth(updated);
        LOG.info("mcp server probed: name={}, status={}, error={}",
                server.name(), status, result.error());
        return updated;
    }

    private String validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw ChatCompletionException.invalidMcpServerRequest("MCP Server 名称不能为空", "name");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "MCP Server 名称不能超过 " + NAME_MAX_LENGTH + " 个字符", "name");
        }
        if (trimmed.contains(TOOL_SEPARATOR)) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "MCP Server 名称不能包含 '" + TOOL_SEPARATOR + "'（工具名分隔符）", "name");
        }
        return trimmed;
    }

    private TransportType validateTransport(String transport) {
        TransportType type = TransportType.from(transport);
        if (Objects.isNull(type)) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "传输方式必须为 http 或 stdio", "transport");
        }
        if (type == TransportType.SSE) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "SSE 传输暂不支持，请使用 http 或 stdio", "transport");
        }
        return type;
    }

    private String normalizeEndpoint(TransportType transport, String endpoint) {
        if (transport != TransportType.HTTP) {
            return null;
        }
        if (!StringUtils.hasText(endpoint)) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "HTTP 传输必须提供 endpoint", "endpoint");
        }
        String trimmed = endpoint.trim();
        if (trimmed.length() > ENDPOINT_MAX_LENGTH) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "endpoint 不能超过 " + ENDPOINT_MAX_LENGTH + " 个字符", "endpoint");
        }
        return trimmed;
    }

    private String validateCommand(TransportType transport, String command) {
        if (transport != TransportType.STDIO) {
            return null;
        }
        if (!StringUtils.hasText(command)) {
            throw ChatCompletionException.invalidMcpServerRequest(
                    "stdio 传输必须提供 command", "command");
        }
        return command.trim();
    }

    private McpServerResDTO toResDTO(McpServerBO server) {
        return new McpServerResDTO(
                server.serverId(),
                server.name(),
                Objects.isNull(server.transport()) ? null : server.transport().value(),
                UrlMasker.mask(server.endpoint()),
                server.command(),
                server.args(),
                server.tenantId(),
                Objects.isNull(server.status()) ? McpServerStatus.DOWN.name() : server.status().name(),
                server.lastHealthAt(),
                server.description(),
                server.lastError(),
                server.createdAt());
    }
}
