package com.qtua.aihub.service;

import com.qtua.aihub.api.McpServerService;
import com.qtua.aihub.api.model.req.McpServerRegisterReqDTO;
import com.qtua.aihub.api.model.res.McpServerResDTO;
import com.qtua.aihub.common.audit.AuditRecorder;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.TransportType;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.common.util.UrlMasker;
import com.qtua.aihub.integration.mcp.McpServerProbe;
import com.qtua.aihub.integration.mcp.ProbeResult;
import com.qtua.aihub.manager.mcp.McpServerManager;
import com.qtua.aihub.manager.mcp.McpToolCatalog;
import com.qtua.aihub.manager.model.McpServerBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ObjectUtils;
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
    private final McpToolCatalog mcpToolCatalog;
    private final TenantScopeGuard tenantScopeGuard;
    private final AuditRecorder auditRecorder;

    /**
     * 兼容构造器（单元测试）：使用无上下文的作用域守卫。
     *
     * @param mcpServerManager MCP Server 数据门面
     * @param mcpServerProbe   探活器
     * @param mcpToolCatalog   工具目录快照
     */
    public McpServerServiceImpl(McpServerManager mcpServerManager, McpServerProbe mcpServerProbe,
                                McpToolCatalog mcpToolCatalog) {
        this(mcpServerManager, mcpServerProbe, mcpToolCatalog, new TenantScopeGuard(),
                AuditRecorder.NOOP);
    }

    /**
     * 构造服务。
     *
     * @param mcpServerManager MCP Server 数据门面
     * @param mcpServerProbe   探活器
     * @param mcpToolCatalog   工具目录快照（注册/注销时失效）
     * @param tenantScopeGuard 租户作用域守卫
     */
    public McpServerServiceImpl(McpServerManager mcpServerManager, McpServerProbe mcpServerProbe,
                                McpToolCatalog mcpToolCatalog, TenantScopeGuard tenantScopeGuard) {
        this(mcpServerManager, mcpServerProbe, mcpToolCatalog, tenantScopeGuard,
                AuditRecorder.NOOP);
    }

    /**
     * 构造服务（含审计）。
     *
     * @param mcpServerManager MCP Server 数据门面
     * @param mcpServerProbe   探活器
     * @param mcpToolCatalog   工具目录快照
     * @param tenantScopeGuard 租户守卫
     * @param auditRecorder    审计端口
     */
    @org.springframework.beans.factory.annotation.Autowired
    public McpServerServiceImpl(McpServerManager mcpServerManager, McpServerProbe mcpServerProbe,
                                McpToolCatalog mcpToolCatalog, TenantScopeGuard tenantScopeGuard,
                                AuditRecorder auditRecorder) {
        this.mcpServerManager = mcpServerManager;
        this.mcpServerProbe = mcpServerProbe;
        this.mcpToolCatalog = mcpToolCatalog;
        this.tenantScopeGuard = tenantScopeGuard;
        this.auditRecorder = auditRecorder;
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

        String tenantId = tenantScopeGuard.resolveTenant(reqDTO.tenantId());
        if (mcpServerManager.findByName(name)
                .filter(existing -> tenantId.equals(existing.tenantId()))
                .isPresent()) {
            throw ChatCompletionException.mcpServerConflict(name);
        }

        McpServerBO server = new McpServerBO(
                UUID.randomUUID().toString(),
                name,
                transport,
                endpoint,
                command,
                reqDTO.args(),
                tenantId,
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
        auditRecorder.record(tenantId, "MCP_REGISTER", name, java.util.Map.of(),
                java.util.Map.of("serverId", server.serverId(), "transport", transport.value()));
        LOG.info("mcp server registered: name={}, transport={}, serverId={}",
                name, transport.value(), server.serverId());
        mcpToolCatalog.invalidate();
        return toResDTO(probeAndUpdate(server));
    }

    /**
     * 查询全部 MCP Server。
     *
     * @return 服务器列表（endpoint 已掩码）
     */
    @Override
    public List<McpServerResDTO> list() {
        TenantIdentity identity = tenantScopeGuard.currentIdentity();
        if (!ObjectUtils.isEmpty(identity) && identity.master()) {
            return mcpServerManager.findAll().stream().map(this::toResDTO).toList();
        }
        String tenantId = tenantScopeGuard.resolveTenant(null);
        return mcpServerManager.findAll().stream()
                .filter(server -> tenantId.equals(server.tenantId()))
                .map(this::toResDTO)
                .toList();
    }

    /**
     * 注销 MCP Server。
     *
     * @param serverId 对外服务器 ID
     */
    @Override
    public void remove(String serverId) {
        McpServerBO server = requireAccessibleServer(serverId);
        if (!mcpServerManager.delete(server.serverId())) {
            throw ChatCompletionException.mcpServerNotFound(serverId);
        }
        auditRecorder.record(ObjectUtils.isEmpty(server.tenantId()) ? "default"
                        : server.tenantId(), "MCP_REMOVE", server.name(),
                java.util.Map.of("serverId", server.serverId(), "transport",
                        server.transport().value()), java.util.Map.of());
        LOG.info("mcp server removed: name={}, serverId={}", server.name(), server.serverId());
        mcpToolCatalog.invalidate();
    }

    /**
     * 校验服务器属于当前租户（master 可访问全部）。
     *
     * @param serverId 服务器 ID
     * @return 服务器
     */
    private McpServerBO requireAccessibleServer(String serverId) {
        McpServerBO server = mcpServerManager.findByServerId(serverId)
                .orElseThrow(() -> ChatCompletionException.mcpServerNotFound(serverId));
        TenantIdentity identity = tenantScopeGuard.currentIdentity();
        if (!ObjectUtils.isEmpty(identity) && identity.master()) {
            return server;
        }
        String tenantId = tenantScopeGuard.resolveTenant(null);
        if (!tenantId.equals(server.tenantId())) {
            throw ChatCompletionException.mcpServerNotFound(serverId);
        }
        return server;
    }

    /**
     * 手动重新探活。
     *
     * @param serverId 对外服务器 ID
     * @return 探活后的最新视图
     */
    @Override
    public McpServerResDTO reprobe(String serverId) {
        McpServerBO server = requireAccessibleServer(serverId);
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
