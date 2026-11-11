package com.qtua.aihub.manager.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.memory.MemoryEntry;
import com.qtua.aihub.common.memory.MemoryQuery;
import com.qtua.aihub.common.memory.MemoryRecallPort;
import com.qtua.aihub.common.memory.MemoryScope;
import com.qtua.aihub.dal.mapper.MemoryAuditMapper;
import com.qtua.aihub.dal.mapper.MemoryMapper;
import com.qtua.aihub.dal.model.MemoryAuditDO;
import com.qtua.aihub.dal.model.MemoryDO;
import com.qtua.aihub.manager.model.MemoryAuditBO;
import com.qtua.aihub.manager.model.MemoryBO;
import com.qtua.aihub.manager.model.MemoryClearResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 记忆数据门面。
 *
 * <p>BO↔DO、metadata JSON 编解码、召回端口的结构化实现、物理删除与审计落库。
 * 事务边界在 Service 层（删除与审计必须同事务）；本类无可变业务状态，多实例安全。</p>
 */
@Component
public class MemoryManager implements MemoryRecallPort {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryManager.class);
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> MEMORY_IDS_TYPE = new TypeReference<>() {
    };
    /** 审计中最多记录的 memory_id 条数，避免大范围删除撑爆单行 JSON。 */
    private static final int MAX_AUDIT_IDS = 1000;
    private static final String OPERATION_CLEAR = "CLEAR";

    private final MemoryMapper memoryMapper;
    private final MemoryAuditMapper memoryAuditMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造门面。
     *
     * @param memoryMapper      记忆 Mapper
     * @param memoryAuditMapper 删除审计 Mapper
     * @param objectMapper      JSON 编解码器
     */
    public MemoryManager(MemoryMapper memoryMapper, MemoryAuditMapper memoryAuditMapper,
                         ObjectMapper objectMapper) {
        this.memoryMapper = memoryMapper;
        this.memoryAuditMapper = memoryAuditMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存一条记忆。
     *
     * @param memory 业务对象
     */
    public void save(MemoryBO memory) {
        MemoryDO dataObject = new MemoryDO();
        dataObject.setMemoryId(memory.memoryId());
        dataObject.setTenantId(memory.tenantId());
        dataObject.setProjectId(memory.projectId());
        dataObject.setUserId(memory.userId());
        dataObject.setSessionId(memory.sessionId());
        dataObject.setMemoryType(memory.memoryType());
        dataObject.setContent(memory.content());
        dataObject.setMetadata(writeMetadata(memory.metadata()));
        dataObject.setExpiresAt(memory.expiresAt());
        dataObject.setCreatedAt(memory.createdAt());
        memoryMapper.insert(dataObject);
    }

    /**
     * 按作用域召回未过期记忆（结构化实现）。
     *
     * @param query 召回条件
     * @return 命中记忆；无命中返回空列表
     */
    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        return memoryMapper.selectForRecall(query.tenantId(), query.projectId(), query.userId(),
                        query.sessionId(), query.keyword(), query.from(), query.to(),
                        query.crossProject(), query.limit())
                .stream()
                .map(this::toEntry)
                .toList();
    }

    /**
     * 按作用域物理删除记忆并写审计。
     *
     * <p>调用方（Service）必须开启事务：删除与审计同成败。删除 0 条时不写审计。</p>
     *
     * @param scope       删除作用域
     * @param reason      删除原因
     * @param operatorKey 执行删除的虚拟 Key 哈希；master 为 null
     * @return 删除条数与审计 ID
     */
    public MemoryClearResult clear(MemoryScope scope, String reason, String operatorKey) {
        List<String> memoryIds = memoryMapper.selectIdsForClear(scope.tenantId(), scope.projectId(),
                scope.userId(), scope.sessionId(), MAX_AUDIT_IDS);
        int deleted = memoryMapper.deleteByScope(scope.tenantId(), scope.projectId(),
                scope.userId(), scope.sessionId());
        if (deleted == 0) {
            return new MemoryClearResult(0, null);
        }
        MemoryAuditDO audit = new MemoryAuditDO();
        audit.setAuditId(UUID.randomUUID().toString());
        audit.setTenantId(scope.tenantId());
        audit.setProjectId(scope.projectId());
        audit.setUserId(scope.userId());
        audit.setSessionId(scope.sessionId());
        audit.setOperation(OPERATION_CLEAR);
        audit.setDeletedCount(deleted);
        audit.setMemoryIds(writeMemoryIds(memoryIds));
        audit.setOperatorKey(operatorKey);
        audit.setReason(reason);
        audit.setCreatedAt(Instant.now());
        memoryAuditMapper.insert(audit);
        LOG.info("memory physical delete audited: auditId={}, deleted={}, recordedIds={}",
                audit.getAuditId(), deleted, memoryIds.size());
        return new MemoryClearResult(deleted, audit.getAuditId());
    }

    /**
     * 查询删除审计（供测试与后续管理端）。
     *
     * @param auditId 审计 ID
     * @return 审计业务对象；不存在为空
     */
    public Optional<MemoryAuditBO> findAudit(String auditId) {
        return memoryAuditMapper.selectByAuditId(auditId).map(this::toAuditBO);
    }

    private MemoryEntry toEntry(MemoryDO dataObject) {
        return new MemoryEntry(dataObject.getMemoryId(), dataObject.getTenantId(),
                dataObject.getProjectId(), dataObject.getUserId(), dataObject.getSessionId(),
                dataObject.getMemoryType(), dataObject.getContent(),
                readMetadata(dataObject.getMetadata()), dataObject.getCreatedAt(),
                dataObject.getExpiresAt());
    }

    private MemoryAuditBO toAuditBO(MemoryAuditDO dataObject) {
        return new MemoryAuditBO(dataObject.getAuditId(), dataObject.getTenantId(),
                dataObject.getProjectId(), dataObject.getUserId(), dataObject.getSessionId(),
                dataObject.getOperation(), dataObject.getDeletedCount(),
                readMemoryIds(dataObject.getMemoryIds()), dataObject.getOperatorKey(),
                dataObject.getReason(), dataObject.getCreatedAt());
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (ObjectUtils.isEmpty(metadata)) {
            return null;
        }
        return writeJson(metadata, "metadata");
    }

    private Map<String, Object> readMetadata(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(json, METADATA_TYPE);
            return ObjectUtils.isEmpty(metadata) ? Map.of() : metadata;
        } catch (JsonProcessingException ignored) {
            // 历史数据损坏不应导致召回整体失败；按空 metadata 返回。
            // 不打印异常：解析异常可能携带 JSON 片段，metadata 属隐私字段。
            LOG.warn("invalid memory metadata JSON, using empty metadata: length={}", json.length());
            return Map.of();
        }
    }

    private String writeMemoryIds(List<String> memoryIds) {
        return writeJson(memoryIds, "memoryIds");
    }

    private List<String> readMemoryIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(json, MEMORY_IDS_TYPE);
            return ObjectUtils.isEmpty(ids) ? List.of() : ids;
        } catch (JsonProcessingException ignored) {
            LOG.warn("invalid memory audit ids JSON: length={}", json.length());
            return List.of();
        }
    }

    private String writeJson(Object value, String field) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize memory " + field, ex);
        }
    }
}
