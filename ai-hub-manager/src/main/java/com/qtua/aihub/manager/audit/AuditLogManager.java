package com.qtua.aihub.manager.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.AuditLogMapper;
import com.qtua.aihub.dal.model.AuditLogDO;
import com.qtua.aihub.manager.model.AuditLogBO;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审计数据门面（REQ-4.5）。
 *
 * <p>只追加与查询；JSON 快照编解码在此完成。</p>
 */
@Component
public class AuditLogManager {

    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {
    };

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造门面。
     *
     * @param auditLogMapper 审计 Mapper
     * @param objectMapper   JSON 编解码
     */
    public AuditLogManager(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 追加审计。
     *
     * @param tenantId 租户
     * @param actor    操作者
     * @param action   动作
     * @param target   目标
     * @param before   变更前
     * @param after    变更后
     * @param traceId  追踪 ID
     */
    public void append(String tenantId, String actor, String action, String target,
                       Map<String, Object> before, Map<String, Object> after, String traceId) {
        AuditLogDO dataObject = new AuditLogDO();
        dataObject.setTenantId(StringUtils.hasText(tenantId) ? tenantId : "default");
        dataObject.setActor(actor);
        dataObject.setAction(action);
        dataObject.setTarget(target);
        dataObject.setBeforeState(writeState(before));
        dataObject.setAfterState(writeState(after));
        dataObject.setTraceId(traceId);
        dataObject.setCreatedAt(Instant.now());
        auditLogMapper.insert(dataObject);
    }

    /**
     * 分页查询。
     *
     * @param tenantId 租户
     * @param action   动作
     * @param target   目标（模糊）
     * @param from     起始时间
     * @param to       结束时间
     * @param limit    每页条数
     * @param offset   偏移
     * @return 记录列表
     */
    public List<AuditLogBO> findPage(String tenantId, String action, String target,
                                     Instant from, Instant to, int limit, int offset) {
        return auditLogMapper.selectPage(tenantId, action, target, from, to, limit, offset)
                .stream()
                .map(this::toBO)
                .toList();
    }

    private AuditLogBO toBO(AuditLogDO dataObject) {
        return new AuditLogBO(ObjectUtils.isEmpty(dataObject.getId()) ? 0L : dataObject.getId(),
                dataObject.getTenantId(), dataObject.getActor(), dataObject.getAction(),
                dataObject.getTarget(), readState(dataObject.getBeforeState()),
                readState(dataObject.getAfterState()), dataObject.getTraceId(),
                dataObject.getCreatedAt());
    }

    private String writeState(Map<String, Object> state) {
        if (ObjectUtils.isEmpty(state)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Map<String, Object> readState(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STATE_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
