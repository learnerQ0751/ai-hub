package com.qtua.aihub.dal.model;

import java.time.Instant;

/**
 * `mcp_servers` 表数据对象。
 *
 * <p>{@code args} 与 {@code metadata} 以 JSON 文本承载，由 manager 层负责与业务对象
 * 互转；dal 层只做字段映射，不解析 JSON。</p>
 */
public class McpServerDO {

    private Long id;
    /** 对外服务器 ID（UUID），业务主键。 */
    private String serverId;
    /** 服务器名，全局唯一；不得包含工具名分隔符 "__"。 */
    private String name;
    /** 传输方式：http/sse/stdio。 */
    private String transport;
    /** HTTP/SSE 端点。 */
    private String endpoint;
    /** stdio 启动命令。 */
    private String command;
    /** stdio 命令参数 JSON 数组。 */
    private String args;
    private String tenantId;
    /** 健康状态：ACTIVE/DOWN。 */
    private String status;
    private Instant lastHealthAt;
    /** 元信息 JSON 对象，含 description / lastError。 */
    private String metadata;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastHealthAt() {
        return lastHealthAt;
    }

    public void setLastHealthAt(Instant lastHealthAt) {
        this.lastHealthAt = lastHealthAt;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
