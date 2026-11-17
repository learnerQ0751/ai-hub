package com.qtua.aihub.service.admin;

import com.qtua.aihub.api.AdminService;
import com.qtua.aihub.api.model.req.AliasUpsertReqDTO;
import com.qtua.aihub.api.model.req.DeploymentUpsertReqDTO;
import com.qtua.aihub.api.model.req.ProjectAuthorizationReqDTO;
import com.qtua.aihub.api.model.res.AdminDeploymentResDTO;
import com.qtua.aihub.api.model.res.AdminKeyPageResDTO;
import com.qtua.aihub.api.model.res.AdminKeyResDTO;
import com.qtua.aihub.api.model.res.AdminLogPageResDTO;
import com.qtua.aihub.api.model.res.AdminLogResDTO;
import com.qtua.aihub.api.model.res.AdminModelResDTO;
import com.qtua.aihub.api.model.res.AdminProjectPageResDTO;
import com.qtua.aihub.api.model.res.AdminProjectResDTO;
import com.qtua.aihub.common.audit.AuditRecorder;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.pricing.ModelPrice;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.manager.ApiKeyManager;
import com.qtua.aihub.manager.CallLogManager;
import com.qtua.aihub.manager.catalog.ModelCatalogManager;
import com.qtua.aihub.manager.model.ApiKeyBO;
import com.qtua.aihub.manager.model.CallLogBO;
import com.qtua.aihub.manager.model.ModelAliasAdminBO;
import com.qtua.aihub.manager.model.ModelDeploymentAdminBO;
import com.qtua.aihub.manager.model.ProjectBO;
import com.qtua.aihub.manager.tenant.ProjectAuthorizationManager;
import com.qtua.aihub.manager.tenant.ProjectManager;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 管理端实现（REQ-4.4）。
 *
 * <p>入口已由 AuthFilter 限制为 master；本类只做参数校验、分页收敛与脱敏，
 * 不返回明文 Key、完整 Key 哈希或上游 api-key。</p>
 */
@Service
public class AdminServiceImpl implements AdminService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern DEPLOYMENT_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._#-]{1,64}");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String RESOURCE_MEMORY = "MEMORY";
    private static final String RESOURCE_KNOWLEDGE = "KNOWLEDGE";
    private static final String RESOURCE_ALL = "ALL";

    private final ApiKeyManager apiKeyManager;
    private final CallLogManager callLogManager;
    private final ModelCatalogManager catalogManager;
    private final ProjectAuthorizationManager authorizationManager;
    private final ProjectManager projectManager;
    private final AuditRecorder auditRecorder;
    private final PricingCatalog pricingCatalog;

    /**
     * 构造服务。
     *
     * @param apiKeyManager        Key 门面
     * @param callLogManager       日志门面
     * @param catalogManager       模型目录门面
     * @param authorizationManager 项目授权门面
     * @param projectManager       项目门面
     * @param auditRecorder        审计端口
     * @param pricingCatalog       模型价格目录
     */
    public AdminServiceImpl(ApiKeyManager apiKeyManager, CallLogManager callLogManager,
                            ModelCatalogManager catalogManager,
                            ProjectAuthorizationManager authorizationManager,
                            ProjectManager projectManager,
                            AuditRecorder auditRecorder, PricingCatalog pricingCatalog) {
        this.apiKeyManager = apiKeyManager;
        this.callLogManager = callLogManager;
        this.catalogManager = catalogManager;
        this.authorizationManager = authorizationManager;
        this.projectManager = projectManager;
        this.auditRecorder = auditRecorder;
        this.pricingCatalog = pricingCatalog;
    }

    @Override
    public AdminKeyPageResDTO listKeys(String tenantId, String projectId, String status,
                                       String model, int limit, int offset) {
        int pageSize = resolveLimit(limit);
        int pageOffset = Math.max(0, offset);
        List<ApiKeyBO> fetched = apiKeyManager.list(trim(tenantId), trim(projectId),
                normalizeKeyStatus(status), trim(model), Instant.now(), pageSize + 1, pageOffset);
        List<AdminKeyResDTO> items = fetched.stream().limit(pageSize)
                .map(this::toKeyDTO).toList();
        return new AdminKeyPageResDTO(items, items.size(), fetched.size() > pageSize);
    }

    @Override
    public AdminKeyResDTO getKey(String keyHash) {
        if (!StringUtils.hasText(keyHash)) {
            throw ChatCompletionException.invalidRequest("key_hash 不能为空", "key_hash");
        }
        return apiKeyManager.findByKeyHash(keyHash.trim()).map(this::toKeyDTO)
                .orElseThrow(ChatCompletionException::apiKeyNotFound);
    }

    @Override
    public AdminProjectPageResDTO listProjects(String tenantId, String status, int limit,
                                               int offset) {
        int pageSize = resolveLimit(limit);
        int pageOffset = Math.max(0, offset);
        List<ProjectBO> fetched = projectManager.list(trim(tenantId),
                normalizeProjectStatus(status), pageSize + 1, pageOffset);
        List<AdminProjectResDTO> items = fetched.stream().limit(pageSize)
                .map(this::toProjectDTO).toList();
        return new AdminProjectPageResDTO(items, items.size(), fetched.size() > pageSize);
    }

    @Override
    public AdminKeyResDTO setKeyBlocked(String keyHash, boolean blocked) {
        if (!StringUtils.hasText(keyHash)) {
            throw ChatCompletionException.invalidRequest("key_hash 不能为空", "key_hash");
        }
        ApiKeyBO key = apiKeyManager.findByKeyHash(keyHash.trim())
                .orElseThrow(ChatCompletionException::apiKeyNotFound);
        boolean changed = apiKeyManager.updateBlocked(key.getKeyHash(), blocked);
        if (changed) {
            auditRecorder.record(ObjectUtils.isEmpty(key.getTenantId()) ? "default"
                            : key.getTenantId(), blocked ? "KEY_BLOCK" : "KEY_UNBLOCK",
                    key.getKeyHash(), java.util.Map.of("blocked", !blocked),
                    java.util.Map.of("blocked", blocked));
        }
        return toKeyDTO(apiKeyManager.findByKeyHash(key.getKeyHash()).orElse(key));
    }

    @Override
    public List<AdminModelResDTO> listModels(String tenantId, String status) {
        String tenantFilter = trim(tenantId);
        String statusFilter = normalizeModelStatus(status);
        return catalogManager.listAliasesAdmin().stream()
                .filter(model -> Objects.isNull(tenantFilter)
                        || tenantFilter.equals(model.tenantId()))
                .filter(model -> Objects.isNull(statusFilter)
                        || statusFilter.equals(model.status()))
                .map(this::toModelDTO)
                .toList();
    }

    @Override
    public AdminModelResDTO getModel(String alias) {
        ModelAliasAdminBO model = catalogManager.findAliasAdmin(alias);
        if (ObjectUtils.isEmpty(model)) {
            throw ChatCompletionException.modelAliasNotFound(alias);
        }
        return toModelDTO(model);
    }

    @Override
    public AdminModelResDTO createModel(AliasUpsertReqDTO reqDTO) {
        String alias = requireAlias(reqDTO.alias());
        // 先整体校验部署，再写库：失败重试不留下半写状态。
        List<ValidatedDeployment> deployments = validateDeployments(alias,
                reqDTO.deployments());
        ModelAliasAdminBO existing = catalogManager.findAliasAdmin(alias);
        if (!ObjectUtils.isEmpty(existing)) {
            if (!sameAlias(existing, reqDTO, deployments)) {
                throw ChatCompletionException.invalidRequest(
                        "模型别名 '" + alias + "' 已存在且配置不同，请使用更新接口", "alias");
            }
            // 幂等重放：补齐上次可能未完成的部署写入。
            applyDeployments(alias, deployments, existing);
            return getModel(alias);
        }
        boolean created = catalogManager.createAlias(alias, trim(reqDTO.tenantId()),
                reqDTO.description(), reqDTO.fallbacks(), resolveStatus(reqDTO.status()));
        if (created) {
            auditRecorder.record(tenantOf(reqDTO.tenantId()), "ALIAS_CREATE", alias,
                    java.util.Map.of(), java.util.Map.of("alias", alias));
        }
        applyDeployments(alias, deployments, catalogManager.findAliasAdmin(alias));
        return getModel(alias);
    }

    @Override
    public AdminModelResDTO updateModel(String alias, AliasUpsertReqDTO reqDTO) {
        ModelAliasAdminBO existing = catalogManager.findAliasAdmin(alias);
        if (ObjectUtils.isEmpty(existing)) {
            throw ChatCompletionException.modelAliasNotFound(alias);
        }
        List<ValidatedDeployment> deployments = validateDeployments(alias,
                reqDTO.deployments());
        boolean changed = !sameAlias(existing, reqDTO, deployments);
        ModelAliasAdminBO updated = catalogManager.updateAlias(alias, trim(reqDTO.tenantId()),
                reqDTO.description(), reqDTO.fallbacks(), resolveStatus(reqDTO.status()));
        if (changed) {
            auditRecorder.record(tenantOf(reqDTO.tenantId()), "ALIAS_UPDATE", alias,
                    java.util.Map.of(), java.util.Map.of("alias", alias));
        }
        applyDeployments(alias, deployments, updated);
        return getModel(alias);
    }

    @Override
    public void deleteModel(String alias) {
        if (!catalogManager.deleteAlias(alias)) {
            throw ChatCompletionException.modelAliasNotFound(alias);
        }
        auditRecorder.record("default", "ALIAS_DELETE", alias, java.util.Map.of("alias", alias),
                java.util.Map.of());
    }

    @Override
    public AdminModelResDTO upsertDeployment(String alias, DeploymentUpsertReqDTO reqDTO) {
        ModelAliasAdminBO existing = catalogManager.findAliasAdmin(alias);
        if (ObjectUtils.isEmpty(existing)) {
            throw ChatCompletionException.modelAliasNotFound(alias);
        }
        ValidatedDeployment deployment = validateDeployment(alias, reqDTO, 0);
        boolean changed = !sameDeployment(existing, deployment);
        catalogManager.upsertDeployment(alias, deployment.deploymentKey(), deployment.apiBase(),
                deployment.apiKey(), deployment.upstreamModel(), deployment.weight(),
                deployment.priority(), deployment.status());
        if (changed) {
            auditRecorder.record("default", "ALIAS_DEPLOYMENT_UPSERT", alias,
                    java.util.Map.of(), java.util.Map.of("alias", alias,
                            "deploymentKey", deployment.deploymentKey()));
        }
        return getModel(alias);
    }

    @Override
    public AdminLogPageResDTO listLogs(String tenantId, String projectId, String model,
                                       String keyId, Integer status, String from, String to,
                                       int limit, int offset) {
        int pageSize = resolveLimit(limit);
        int pageOffset = Math.max(0, offset);
        List<CallLogBO> fetched = callLogManager.findPage(trim(tenantId), trim(projectId),
                trim(model), trim(keyId), status, parseInstant(from, "from"),
                parseInstant(to, "to"), pageSize + 1, pageOffset);
        List<AdminLogResDTO> items = fetched.stream().limit(pageSize).map(this::toLogDTO)
                .toList();
        return new AdminLogPageResDTO(items, items.size(), fetched.size() > pageSize);
    }

    @Override
    public void authorizeProject(ProjectAuthorizationReqDTO reqDTO) {
        String tenantId = requireText(reqDTO.tenantId(), "tenant_id");
        String source = requireText(reqDTO.sourceProjectId(), "source_project_id");
        String target = requireText(reqDTO.targetProjectId(), "target_project_id");
        if (source.equals(target)) {
            throw ChatCompletionException.invalidRequest(
                    "source_project_id 与 target_project_id 不能相同", "target_project_id");
        }
        String resourceType = StringUtils.hasText(reqDTO.resourceType())
                ? reqDTO.resourceType().trim().toUpperCase() : RESOURCE_MEMORY;
        if (!RESOURCE_MEMORY.equals(resourceType) && !RESOURCE_KNOWLEDGE.equals(resourceType)
                && !RESOURCE_ALL.equals(resourceType)) {
            throw ChatCompletionException.invalidRequest(
                    "resource_type 必须为 MEMORY/KNOWLEDGE/ALL", "resource_type");
        }
        boolean created = authorizationManager.authorize(tenantId, source, target, resourceType);
        if (created) {
            auditRecorder.record(tenantId, "PROJECT_AUTHORIZE", source + "->" + target,
                    java.util.Map.of(), java.util.Map.of("source", source, "target", target,
                            "resourceType", resourceType));
        }
    }

    private String tenantOf(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : "default";
    }

    private List<ValidatedDeployment> validateDeployments(String alias,
                                                          List<DeploymentUpsertReqDTO> deployments) {
        if (CollectionUtils.isEmpty(deployments)) {
            return List.of();
        }
        List<ValidatedDeployment> validated = new java.util.ArrayList<>(deployments.size());
        for (int index = 0; index < deployments.size(); index++) {
            validated.add(validateDeployment(alias, deployments.get(index), index));
        }
        return validated;
    }

    private ValidatedDeployment validateDeployment(String alias, DeploymentUpsertReqDTO reqDTO,
                                                   int index) {
        String apiBase = requireText(reqDTO.apiBase(), "api_base");
        String upstreamModel = requireText(reqDTO.upstreamModel(), "upstream_model");
        String deploymentKey = StringUtils.hasText(reqDTO.deploymentKey())
                ? reqDTO.deploymentKey().trim() : alias + "#" + index;
        if (!DEPLOYMENT_KEY_PATTERN.matcher(deploymentKey).matches()) {
            throw ChatCompletionException.invalidRequest(
                    "deployment_key 必须为 1-64 位字母、数字、点、下划线、中划线或 #",
                    "deployment_key");
        }
        int weight = Objects.isNull(reqDTO.weight()) || reqDTO.weight() <= 0
                ? 1 : reqDTO.weight();
        int priority = Objects.isNull(reqDTO.priority()) || reqDTO.priority() < 0
                ? 0 : reqDTO.priority();
        return new ValidatedDeployment(deploymentKey, apiBase, reqDTO.apiKey(), upstreamModel,
                weight, priority, resolveStatus(reqDTO.status()));
    }

    private void applyDeployments(String alias, List<ValidatedDeployment> deployments,
                                  ModelAliasAdminBO current) {
        for (ValidatedDeployment deployment : deployments) {
            catalogManager.upsertDeployment(alias, deployment.deploymentKey(),
                    deployment.apiBase(), deployment.apiKey(), deployment.upstreamModel(),
                    deployment.weight(), deployment.priority(), deployment.status());
        }
    }

    private boolean sameAlias(ModelAliasAdminBO existing, AliasUpsertReqDTO reqDTO,
                              List<ValidatedDeployment> deployments) {
        if (!Objects.equals(existing.tenantId(), trim(reqDTO.tenantId()))
                || !Objects.equals(existing.description(), reqDTO.description())
                || !Objects.equals(existing.status(), resolveStatus(reqDTO.status()))
                || !Objects.equals(normalizeList(existing.fallbacks()),
                normalizeList(reqDTO.fallbacks()))) {
            return false;
        }
        for (ValidatedDeployment deployment : deployments) {
            if (!sameDeployment(existing, deployment)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameDeployment(ModelAliasAdminBO existing, ValidatedDeployment deployment) {
        return existing.deployments().stream()
                .filter(item -> deployment.deploymentKey().equals(item.deploymentKey()))
                .findFirst()
                .map(item -> Objects.equals(item.apiBase(), deployment.apiBase())
                        && Objects.equals(item.upstreamModel(), deployment.upstreamModel())
                        && item.weight() == deployment.weight()
                        && item.priority() == deployment.priority()
                        && Objects.equals(item.status(), deployment.status()))
                .orElse(false);
    }

    private List<String> normalizeList(List<String> values) {
        return CollectionUtils.isEmpty(values) ? List.of() : values;
    }

    /**
     * 校验后的部署写入参数。
     *
     * @param deploymentKey 部署键
     * @param apiBase       上游地址
     * @param apiKey        上游 Key 明文（写库前加密）
     * @param upstreamModel 上游模型
     * @param weight        权重
     * @param priority      优先级
     * @param status        状态
     */
    private record ValidatedDeployment(String deploymentKey, String apiBase, String apiKey,
                                       String upstreamModel, int weight, int priority,
                                       String status) {
    }

    private String resolveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw ChatCompletionException.invalidRequest(
                    "status 必须为 ACTIVE 或 DISABLED", "status");
        }
        return normalized;
    }

    private String requireAlias(String alias) {
        if (!StringUtils.hasText(alias) || !ALIAS_PATTERN.matcher(alias.trim()).matches()) {
            throw ChatCompletionException.invalidRequest(
                    "alias 必须为 1-128 位字母、数字、点、下划线或中划线", "alias");
        }
        return alias.trim();
    }

    private String requireText(String value, String param) {
        if (!StringUtils.hasText(value)) {
            throw ChatCompletionException.invalidRequest(param + " 不能为空", param);
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    param + " 长度不能超过 " + MAX_TEXT_LENGTH, param);
        }
        return trimmed;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int resolveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Instant parseInstant(String value, String param) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw ChatCompletionException.invalidRequest(
                    param + " 必须为 ISO-8601 时间（如 2026-11-16T00:00:00Z）", param);
        }
    }

    private AdminKeyResDTO toKeyDTO(ApiKeyBO key) {
        return new AdminKeyResDTO(key.getKeyHash(), key.getAlias(), key.getModels(),
                key.getSpend(), key.getMaxBudget(), key.getBudgetDuration(),
                key.getRpmLimit(), key.getTpmLimit(), key.getMetadata(), key.isBlocked(),
                key.getTenantId(), key.getProjectId(), iso(key.getExpiresAt()),
                iso(key.getCreatedAt()));
    }

    private AdminProjectResDTO toProjectDTO(ProjectBO project) {
        return new AdminProjectResDTO(project.tenantId(), project.projectId(), project.name(),
                project.status(), iso(project.createdAt()));
    }

    private AdminModelResDTO toModelDTO(ModelAliasAdminBO model) {
        List<AdminDeploymentResDTO> deployments = model.deployments().stream()
                .map(this::toDeploymentDTO)
                .toList();
        ModelPrice price = model.deployments().isEmpty()
                ? null : pricingCatalog.findPrice(model.deployments().get(0).upstreamModel())
                .orElse(null);
        return new AdminModelResDTO(model.alias(), model.tenantId(), model.description(),
                model.status(), model.fallbacks(),
                Objects.isNull(price) ? null : price.inputPerMillion(),
                Objects.isNull(price) ? null : price.outputPerMillion(),
                iso(model.createdAt()), iso(model.updatedAt()), deployments);
    }

    private String normalizeKeyStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        if (!"active".equals(normalized) && !"blocked".equals(normalized)
                && !"expired".equals(normalized)) {
            throw ChatCompletionException.invalidRequest(
                    "status 必须为 active、blocked 或 expired", "status");
        }
        return normalized;
    }

    private String normalizeProjectStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw ChatCompletionException.invalidRequest(
                    "status 必须为 ACTIVE 或 DISABLED", "status");
        }
        return normalized;
    }

    private String normalizeModelStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw ChatCompletionException.invalidRequest(
                    "status 必须为 ACTIVE 或 DISABLED", "status");
        }
        return normalized;
    }

    private AdminDeploymentResDTO toDeploymentDTO(ModelDeploymentAdminBO deployment) {
        return new AdminDeploymentResDTO(deployment.deploymentKey(), deployment.apiBase(),
                deployment.upstreamModel(), deployment.weight(), deployment.priority(),
                deployment.status());
    }

    private AdminLogResDTO toLogDTO(CallLogBO log) {
        return new AdminLogResDTO(log.requestId(), log.requestId(), log.keyId(),
                log.tenantId(), log.projectId(), log.modelAlias(), log.deploymentModel(),
                log.usage().promptTokens(), log.usage().completionTokens(),
                log.usage().totalTokens(), log.cost(), log.latencyMs(), log.status(),
                log.errorType(), log.callType(), log.targetName(), iso(log.createdAt()));
    }

    private String iso(Instant instant) {
        return ObjectUtils.isEmpty(instant) ? null : instant.toString();
    }
}
