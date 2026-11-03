package com.qtua.aihub.manager.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.TransportType;
import com.qtua.aihub.dal.mapper.McpServerMapper;
import com.qtua.aihub.dal.model.McpServerDO;
import com.qtua.aihub.manager.model.McpServerBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP Server 数据门面。
 *
 * <p>负责 BO↔DO 与 JSON 字段（args/metadata）编解码，不做业务判断；
 * 注册校验、探活编排在 service 层。</p>
 */
@Component
public class McpServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerManager.class);
    private static final TypeReference<List<String>> ARGS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_LAST_ERROR = "lastError";

    private final McpServerMapper mcpServerMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造数据门面。
     *
     * @param mcpServerMapper MCP Server 数据访问 Mapper
     * @param objectMapper    JSON 编解码器
     */
    public McpServerManager(McpServerMapper mcpServerMapper, ObjectMapper objectMapper) {
        this.mcpServerMapper = mcpServerMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存 MCP Server。
     *
     * @param server 业务对象
     */
    public void save(McpServerBO server) {
        McpServerDO dataObject = new McpServerDO();
        dataObject.setServerId(server.serverId());
        dataObject.setName(server.name());
        dataObject.setTransport(server.transport().value());
        dataObject.setEndpoint(server.endpoint());
        dataObject.setCommand(server.command());
        dataObject.setArgs(writeJson(server.args()));
        dataObject.setTenantId(server.tenantId());
        dataObject.setStatus(server.status().name());
        dataObject.setLastHealthAt(server.lastHealthAt());
        dataObject.setMetadata(writeMetadata(server.description(), server.lastError()));
        dataObject.setCreatedAt(server.createdAt());
        mcpServerMapper.insert(dataObject);
    }

    /**
     * 按 server_id 查询。
     *
     * @param serverId 对外服务器 ID
     * @return 业务对象；不存在时为空
     */
    public Optional<McpServerBO> findByServerId(String serverId) {
        return mcpServerMapper.selectByServerId(serverId).map(this::toBO);
    }

    /**
     * 按名称查询（注册查重）。
     *
     * @param name 服务器名
     * @return 业务对象；不存在时为空
     */
    public Optional<McpServerBO> findByName(String name) {
        return mcpServerMapper.selectByName(name).map(this::toBO);
    }

    /**
     * 查询全部服务器。
     *
     * @return 业务对象列表
     */
    public List<McpServerBO> findAll() {
        return mcpServerMapper.selectAll().stream().map(this::toBO).toList();
    }

    /**
     * 查询 ACTIVE 服务器（启动加载）。
     *
     * @return 活跃服务器列表
     */
    public List<McpServerBO> findActive() {
        return mcpServerMapper.selectActive().stream().map(this::toBO).toList();
    }

    /**
     * 更新健康状态与最近错误。
     *
     * @param server 已带新状态的业务对象
     * @return 是否更新成功
     */
    public boolean updateHealth(McpServerBO server) {
        return mcpServerMapper.updateHealth(server.serverId(), server.status().name(),
                server.lastHealthAt(),
                writeMetadata(server.description(), server.lastError())) > 0;
    }

    /**
     * 按 server_id 删除。
     *
     * @param serverId 对外服务器 ID
     * @return 是否删除成功
     */
    public boolean delete(String serverId) {
        return mcpServerMapper.deleteByServerId(serverId) > 0;
    }

    private McpServerBO toBO(McpServerDO dataObject) {
        Map<String, Object> metadata = readMetadata(dataObject.getMetadata());
        return new McpServerBO(
                dataObject.getServerId(),
                dataObject.getName(),
                TransportType.from(dataObject.getTransport()),
                dataObject.getEndpoint(),
                dataObject.getCommand(),
                readArgs(dataObject.getArgs()),
                dataObject.getTenantId(),
                parseStatus(dataObject.getStatus()),
                dataObject.getLastHealthAt(),
                asString(metadata.get(KEY_DESCRIPTION)),
                asString(metadata.get(KEY_LAST_ERROR)),
                dataObject.getCreatedAt());
    }

    private McpServerStatus parseStatus(String status) {
        if (McpServerStatus.ACTIVE.name().equalsIgnoreCase(status)) {
            return McpServerStatus.ACTIVE;
        }
        return McpServerStatus.DOWN;
    }

    private List<String> readArgs(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return List.of();
        }
        try {
            List<String> args = objectMapper.readValue(json, ARGS_TYPE);
            return ObjectUtils.isEmpty(args) ? List.of() : args;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid args JSON in mcp_servers", ex);
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid metadata JSON in mcp_servers", ex);
        }
    }

    private String writeMetadata(String description, String lastError) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.hasText(description)) {
            metadata.put(KEY_DESCRIPTION, description);
        }
        if (StringUtils.hasText(lastError)) {
            metadata.put(KEY_LAST_ERROR, lastError);
        }
        return writeJson(metadata);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            LOG.error("failed to serialize mcp server field", ex);
            throw new IllegalStateException("Failed to serialize mcp server field", ex);
        }
    }

    private String asString(Object value) {
        return ObjectUtils.isEmpty(value) ? null : value.toString();
    }
}
