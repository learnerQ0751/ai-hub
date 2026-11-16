package com.qtua.aihub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.MemoryService;
import com.qtua.aihub.api.model.req.MemoryClearReqDTO;
import com.qtua.aihub.api.model.req.MemoryRecallReqDTO;
import com.qtua.aihub.api.model.req.MemorySaveReqDTO;
import com.qtua.aihub.api.model.res.MemoryClearResDTO;
import com.qtua.aihub.api.model.res.MemoryRecallResDTO;
import com.qtua.aihub.api.model.res.MemoryResDTO;
import com.qtua.aihub.api.model.res.MemorySaveResDTO;
import com.qtua.aihub.common.config.MemoryProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.memory.MemoryEntry;
import com.qtua.aihub.common.memory.MemoryQuery;
import com.qtua.aihub.common.memory.MemoryScope;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.tenant.TenantScope;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.manager.memory.MemoryManager;
import com.qtua.aihub.manager.model.MemoryBO;
import com.qtua.aihub.manager.model.MemoryClearResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 记忆服务实现。
 *
 * <p>职责：参数校验、作用域授权（跨项目共享开关）、TTL 计算、DTO↔BO 转换；
 * clear 在 Service 事务边界内执行「查询 id → 物理删除 → 写审计」，两者同成败。
 * 日志只记录 id/长度/计数，不记录 content 与 metadata。</p>
 */
@Service
public class MemoryServiceImpl implements MemoryService {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryServiceImpl.class);

    private static final int MAX_TENANT_LENGTH = 64;
    private static final int MAX_PROJECT_LENGTH = 64;
    private static final int MAX_USER_LENGTH = 128;
    private static final int MAX_SESSION_LENGTH = 128;
    private static final int MAX_KEYWORD_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 512;
    private static final String PARAM_TENANT = "tenant_id";
    private static final String PARAM_PROJECT = "project_id";
    private static final String PARAM_USER = "user_id";
    private static final String PARAM_SESSION = "session_id";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_METADATA = "metadata";
    private static final String PARAM_TTL = "ttl_seconds";
    private static final String PARAM_KEYWORD = "keyword";
    private static final String PARAM_FROM = "from";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_REASON = "reason";
    private static final String CROSS_PROJECT_DISABLED =
            "未开启跨项目记忆共享，无法跨项目召回；请使用项目内作用域或联系管理员开启。";
    private static final String SESSION_CROSS_PROJECT_DISABLED =
            "会话级记忆不支持跨项目召回，请去掉 session_id 或关闭 cross_project。";

    private final MemoryManager memoryManager;
    private final MemoryProperties properties;
    private final ObjectMapper objectMapper;
    private final TenantScopeGuard tenantScopeGuard;

    /**
     * 兼容构造器（单元测试）：使用无上下文的作用域守卫。
     *
     * @param memoryManager 记忆数据门面
     * @param properties    记忆配置
     * @param objectMapper  JSON 序列化器
     */
    public MemoryServiceImpl(MemoryManager memoryManager, MemoryProperties properties,
                             ObjectMapper objectMapper) {
        this(memoryManager, properties, objectMapper, new TenantScopeGuard());
    }

    /**
     * 构造服务。
     *
     * @param memoryManager    记忆数据门面
     * @param properties       记忆配置
     * @param objectMapper     JSON 序列化器（metadata 大小校验）
     * @param tenantScopeGuard 租户作用域守卫
     */
    @org.springframework.beans.factory.annotation.Autowired
    public MemoryServiceImpl(MemoryManager memoryManager, MemoryProperties properties,
                             ObjectMapper objectMapper, TenantScopeGuard tenantScopeGuard) {
        this.memoryManager = memoryManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    /**
     * 保存记忆：会话级绑定 session，用户级 session_id 为空。
     *
     * @param reqDTO 保存参数
     * @return 记忆 ID、类型与过期时间
     */
    @Override
    public MemorySaveResDTO save(MemorySaveReqDTO reqDTO) {
        TenantScope scope = tenantScopeGuard.resolve(reqDTO.tenantId(), reqDTO.projectId());
        validateScope(scope.tenantId(), scope.projectId(), reqDTO.userId(), reqDTO.sessionId());
        String content = validateContent(reqDTO.content());
        Map<String, Object> metadata = validateMetadata(reqDTO.metadata());
        Instant now = Instant.now();
        Instant expiresAt = resolveExpiresAt(reqDTO.ttlSeconds(), now);
        String sessionId = trimToNull(reqDTO.sessionId());
        String memoryType = Objects.isNull(sessionId) ? MemoryBO.TYPE_USER : MemoryBO.TYPE_SESSION;
        MemoryBO memory = new MemoryBO(UUID.randomUUID().toString(), scope.tenantId(),
                scope.projectId(), reqDTO.userId().trim(), sessionId, memoryType,
                content, metadata, expiresAt, now);
        memoryManager.save(memory);
        LOG.info("memory saved: memoryId={}, tenantId={}, projectId={}, userId={}, sessionId={}, "
                        + "memoryType={}, contentLength={}, hasMetadata={}",
                memory.memoryId(), memory.tenantId(), memory.projectId(), memory.userId(),
                memory.sessionId(), memory.memoryType(), content.length(), !metadata.isEmpty());
        return toSaveResDTO(memory);
    }

    /**
     * 召回记忆：session_id 决定会话级/用户级；跨项目仅用户级且配置开启时允许。
     *
     * @param reqDTO 召回参数
     * @return 命中记忆；无命中返回空列表
     */
    @Override
    public MemoryRecallResDTO recall(MemoryRecallReqDTO reqDTO) {
        TenantScope scope = tenantScopeGuard.resolve(reqDTO.tenantId(), reqDTO.projectId());
        validateScope(scope.tenantId(), scope.projectId(), reqDTO.userId(), reqDTO.sessionId());
        String sessionId = trimToNull(reqDTO.sessionId());
        boolean crossProject = Boolean.TRUE.equals(reqDTO.crossProject());
        validateCrossProject(crossProject, sessionId);
        validateCrossProjectAuthorization(crossProject);
        String keywordPattern = keywordPattern(reqDTO.keyword());
        validateTimeRange(reqDTO.from(), reqDTO.to());
        int limit = resolveLimit(reqDTO.limit());
        MemoryQuery query = new MemoryQuery(scope.tenantId(), scope.projectId(),
                reqDTO.userId().trim(), sessionId, keywordPattern, reqDTO.from(), reqDTO.to(),
                limit, crossProject);
        List<MemoryEntry> entries = memoryManager.recall(query);
        LOG.info("memory recalled: tenantId={}, projectId={}, userId={}, sessionId={}, "
                        + "crossProject={}, keywordPresent={}, count={}",
                query.tenantId(), query.projectId(), query.userId(), query.sessionId(),
                crossProject, Objects.nonNull(keywordPattern), entries.size());
        return new MemoryRecallResDTO(entries.stream().map(this::toResDTO).toList(),
                entries.size());
    }

    /**
     * 清理记忆：物理删除并写审计，删除与审计同事务。
     *
     * @param reqDTO      清理参数
     * @param operatorKey 执行删除的虚拟 Key 哈希；master 为 null
     * @return 删除条数与审计 ID
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public MemoryClearResDTO clear(MemoryClearReqDTO reqDTO, String operatorKey) {
        TenantScope effective = tenantScopeGuard.resolve(reqDTO.tenantId(), reqDTO.projectId());
        validateScope(effective.tenantId(), effective.projectId(), reqDTO.userId(),
                reqDTO.sessionId());
        String reason = validateReason(reqDTO.reason());
        MemoryScope scope = new MemoryScope(effective.tenantId(), effective.projectId(),
                reqDTO.userId().trim(), trimToNull(reqDTO.sessionId()));
        MemoryClearResult result = memoryManager.clear(scope, reason, operatorKey);
        LOG.info("memory cleared: tenantId={}, projectId={}, userId={}, sessionId={}, deleted={}, "
                        + "auditId={}",
                scope.tenantId(), scope.projectId(), scope.userId(), scope.sessionId(),
                result.deletedCount(), result.auditId());
        return new MemoryClearResDTO(result.deletedCount(), result.auditId());
    }

    /**
     * 校验作用域字段：tenant/project/user 必填，session 可选但有长度上限。
     *
     * @param tenantId  租户 ID
     * @param projectId 项目 ID
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     */
    private void validateScope(String tenantId, String projectId, String userId, String sessionId) {
        requireText(tenantId, PARAM_TENANT, MAX_TENANT_LENGTH);
        requireText(projectId, PARAM_PROJECT, MAX_PROJECT_LENGTH);
        requireText(userId, PARAM_USER, MAX_USER_LENGTH);
        if (StringUtils.hasText(sessionId) && sessionId.trim().length() > MAX_SESSION_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    PARAM_SESSION + " 长度不能超过 " + MAX_SESSION_LENGTH + " 个字符",
                    PARAM_SESSION);
        }
    }

    /**
     * 校验必填文本字段并返回去空格后的值。
     *
     * @param value     字段值
     * @param param     字段名
     * @param maxLength 长度上限
     * @return 去空格后的值
     */
    private String requireText(String value, String param, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw ChatCompletionException.invalidRequest(param + " 不能为空", param);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ChatCompletionException.invalidRequest(
                    param + " 长度不能超过 " + maxLength + " 个字符", param);
        }
        return trimmed;
    }

    private String validateContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw ChatCompletionException.invalidRequest("记忆内容不能为空", PARAM_CONTENT);
        }
        if (content.length() > properties.getMaxContentLength()) {
            throw ChatCompletionException.invalidRequest(
                    "记忆内容不能超过 " + properties.getMaxContentLength() + " 个字符",
                    PARAM_CONTENT);
        }
        return content;
    }

    private Map<String, Object> validateMetadata(Map<String, Object> metadata) {
        if (ObjectUtils.isEmpty(metadata)) {
            return Map.of();
        }
        try {
            String json = objectMapper.writeValueAsString(metadata);
            if (json.length() > properties.getMaxMetadataLength()) {
                throw ChatCompletionException.invalidRequest(
                        "metadata 序列化后不能超过 " + properties.getMaxMetadataLength()
                                + " 个字符", PARAM_METADATA);
            }
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.invalidRequest("metadata 不是合法的 JSON 对象",
                    PARAM_METADATA);
        }
        return metadata;
    }

    private Instant resolveExpiresAt(Long ttlSeconds, Instant now) {
        if (Objects.isNull(ttlSeconds)) {
            return null;
        }
        if (ttlSeconds <= 0) {
            throw ChatCompletionException.invalidRequest(PARAM_TTL + " 必须大于 0", PARAM_TTL);
        }
        if (ttlSeconds > properties.getMaxTtlSeconds()) {
            throw ChatCompletionException.invalidRequest(
                    PARAM_TTL + " 不能超过 " + properties.getMaxTtlSeconds() + " 秒", PARAM_TTL);
        }
        return now.plusSeconds(ttlSeconds);
    }

    /**
     * 虚拟 Key 跨项目召回必须存在项目级授权（REQ-4.1）。
     *
     * @param crossProject 是否请求跨项目
     */
    private void validateCrossProjectAuthorization(boolean crossProject) {
        if (!crossProject) {
            return;
        }
        TenantIdentity identity = tenantScopeGuard.currentIdentity();
        if (!ObjectUtils.isEmpty(identity) && !identity.master() && identity.authorizedProjects().isEmpty()) {
            throw ChatCompletionException.memoryAccessDenied(
                    "当前 API Key 未被授权跨项目访问记忆，请联系管理员配置项目授权。");
        }
    }

    private void validateCrossProject(boolean crossProject, String sessionId) {
        if (!crossProject) {
            return;
        }
        if (Objects.nonNull(sessionId)) {
            throw ChatCompletionException.invalidRequest(SESSION_CROSS_PROJECT_DISABLED,
                    PARAM_SESSION);
        }
        if (!properties.isCrossProjectSharingEnabled()) {
            throw ChatCompletionException.memoryAccessDenied(CROSS_PROJECT_DISABLED);
        }
    }

    private String keywordPattern(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    PARAM_KEYWORD + " 长度不能超过 " + MAX_KEYWORD_LENGTH + " 个字符",
                    PARAM_KEYWORD);
        }
        // 转义 LIKE 通配符，避免用户输入 % / _ 扩大匹配范围。
        String escaped = trimmed.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (Objects.nonNull(from) && Objects.nonNull(to) && from.isAfter(to)) {
            throw ChatCompletionException.invalidRequest(PARAM_FROM + " 不能晚于 to",
                    PARAM_FROM);
        }
    }

    private int resolveLimit(Integer limit) {
        if (Objects.isNull(limit)) {
            return properties.getDefaultRecallLimit();
        }
        if (limit <= 0) {
            throw ChatCompletionException.invalidRequest(PARAM_LIMIT + " 必须大于 0",
                    PARAM_LIMIT);
        }
        return Math.min(limit, properties.getMaxRecallLimit());
    }

    private String validateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    PARAM_REASON + " 长度不能超过 " + MAX_REASON_LENGTH + " 个字符", PARAM_REASON);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private MemorySaveResDTO toSaveResDTO(MemoryBO memory) {
        return new MemorySaveResDTO(memory.memoryId(), memory.tenantId(), memory.projectId(),
                memory.userId(), memory.sessionId(), memory.memoryType(), memory.createdAt(),
                memory.expiresAt());
    }

    private MemoryResDTO toResDTO(MemoryEntry entry) {
        return new MemoryResDTO(entry.memoryId(), entry.tenantId(), entry.projectId(),
                entry.userId(), entry.sessionId(), entry.memoryType(), entry.content(),
                entry.metadata(), entry.createdAt(), entry.expiresAt());
    }
}
