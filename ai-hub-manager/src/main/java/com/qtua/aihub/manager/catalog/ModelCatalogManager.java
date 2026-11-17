package com.qtua.aihub.manager.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.crypto.SecretCipher;
import com.qtua.aihub.dal.mapper.ModelCatalogMapper;
import com.qtua.aihub.dal.model.ModelAliasDO;
import com.qtua.aihub.dal.model.ModelCatalogRow;
import com.qtua.aihub.dal.model.ModelDeploymentDO;
import com.qtua.aihub.manager.model.ModelAliasAdminBO;
import com.qtua.aihub.manager.model.ModelDeploymentAdminBO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型目录数据门面（DB 化，REQ-4.1/4.4）。
 *
 * <p>读取联表结果并解密上游凭证；写入由管理端调用。目录数据量小，
 * 每次请求读库以换取多实例一致性，不做本机缓存。</p>
 */
@Component
public class ModelCatalogManager {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ModelCatalogMapper catalogMapper;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;

    /**
     * 构造门面。
     *
     * @param catalogMapper 模型目录 Mapper
     * @param secretCipher  凭证加解密
     * @param objectMapper  JSON 编解码
     */
    public ModelCatalogManager(ModelCatalogMapper catalogMapper, SecretCipher secretCipher,
                               ObjectMapper objectMapper) {
        this.catalogMapper = catalogMapper;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
    }

    /**
     * 别名 → 部署列表（已解密凭证）。
     *
     * @return 有序映射
     */
    public Map<String, List<ModelDeployment>> deploymentsByAlias() {
        Map<String, List<ModelDeployment>> mapped = new LinkedHashMap<>();
        for (ModelCatalogRow row : catalogMapper.selectCatalogRows()) {
            mapped.computeIfAbsent(row.getAlias(), alias -> new java.util.ArrayList<>())
                    .add(new ModelDeployment(row.getAlias(), row.getApiBase(),
                            secretCipher.decrypt(row.getApiKeyCipher()), row.getUpstreamModel(),
                            Math.max(1, row.getWeight()), Math.max(0, row.getPriority())));
        }
        return mapped;
    }

    /**
     * 别名 → fallback 链。
     *
     * @return 有序映射
     */
    public Map<String, List<String>> fallbacksByAlias() {
        Map<String, List<String>> fallbacks = new LinkedHashMap<>();
        for (ModelCatalogRow row : catalogMapper.selectCatalogRows()) {
            fallbacks.putIfAbsent(row.getAlias(), readFallbacks(row.getFallbacks()));
        }
        return fallbacks;
    }

    /**
     * 全部激活别名（含无部署的别名）。
     *
     * @return 有序别名集合
     */
    public Set<String> aliasNames() {
        return new LinkedHashSet<>(catalogMapper.selectAliasNames());
    }

    /**
     * 是否已有目录数据（启动种子判断）。
     *
     * @return true 表示非空
     */
    public boolean hasAliases() {
        return catalogMapper.countAliases() > 0;
    }

    /**
     * 解析 fallback JSON。
     *
     * @param json JSON 文本
     * @return 别名列表
     */
    public List<String> readFallbacks(String json) {
        if (!org.springframework.util.StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid fallbacks JSON in model_aliases", ex);
        }
    }

    /**
     * 序列化 fallback 列表。
     *
     * @param fallbacks 别名列表
     * @return JSON 文本
     */
    public String writeFallbacks(List<String> fallbacks) {
        if (ObjectUtils.isEmpty(fallbacks)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fallbacks);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize fallbacks", ex);
        }
    }

    /**
     * 管理端别名列表（含部署状态，不含凭证）。
     *
     * @return 别名列表
     */
    public List<ModelAliasAdminBO> listAliasesAdmin() {
        return catalogMapper.selectAllAliases().stream().map(this::toAdminBO).toList();
    }

    /**
     * 管理端单个别名。
     *
     * @param alias 别名
     * @return 别名视图；不存在为 null
     */
    public ModelAliasAdminBO findAliasAdmin(String alias) {
        return catalogMapper.selectByAlias(alias).map(this::toAdminBO).orElse(null);
    }

    /**
     * 新增别名。
     *
     * @param alias       别名
     * @param tenantId    租户
     * @param description 描述
     * @param fallbacks   降级链
     * @param status      状态
     * @return true 表示本次插入成功；false 表示已存在
     */
    public boolean createAlias(String alias, String tenantId, String description,
                               List<String> fallbacks, String status) {
        ModelAliasDO aliasDO = new ModelAliasDO();
        aliasDO.setAlias(alias);
        aliasDO.setTenantId(tenantId);
        aliasDO.setDescription(description);
        aliasDO.setFallbacks(writeFallbacks(fallbacks));
        aliasDO.setStatus(status);
        java.time.Instant now = java.time.Instant.now();
        aliasDO.setCreatedAt(now);
        aliasDO.setUpdatedAt(now);
        try {
            catalogMapper.insertAlias(aliasDO);
            return true;
        } catch (DuplicateKeyException ex) {
            // 并发生成同一别名：唯一约束兜底，视为已存在。
            return false;
        }
    }

    /**
     * 更新别名描述/降级链/状态。
     *
     * @param alias       别名
     * @param tenantId    租户
     * @param description 描述
     * @param fallbacks   降级链
     * @param status      状态
     * @return 别名视图
     */
    public ModelAliasAdminBO updateAlias(String alias, String tenantId, String description,
                                         List<String> fallbacks, String status) {
        ModelAliasDO aliasDO = catalogMapper.selectByAlias(alias).orElse(null);
        if (ObjectUtils.isEmpty(aliasDO)) {
            return null;
        }
        aliasDO.setTenantId(tenantId);
        aliasDO.setDescription(description);
        aliasDO.setFallbacks(writeFallbacks(fallbacks));
        aliasDO.setStatus(status);
        aliasDO.setUpdatedAt(java.time.Instant.now());
        catalogMapper.updateAlias(aliasDO);
        return findAliasAdmin(alias);
    }

    /**
     * 删除别名及关联（部署实例保留）。
     *
     * @param alias 别名
     * @return 是否删除
     */
    public boolean deleteAlias(String alias) {
        ModelAliasDO aliasDO = catalogMapper.selectByAlias(alias).orElse(null);
        if (ObjectUtils.isEmpty(aliasDO)) {
            return false;
        }
        catalogMapper.deleteAliasDeployments(aliasDO.getId());
        catalogMapper.deleteAlias(alias);
        return true;
    }

    /**
     * 新增/更新部署实例并关联别名。
     *
     * @param alias         别名
     * @param deploymentKey 部署键
     * @param apiBase       上游地址
     * @param apiKeyPlain   上游 Key 明文（入库加密）
     * @param upstreamModel 上游模型
     * @param weight        权重
     * @param priority      优先级
     * @param status        状态
     * @return 别名视图；别名不存在为 null
     */
    public ModelAliasAdminBO upsertDeployment(String alias, String deploymentKey, String apiBase,
                                              String apiKeyPlain, String upstreamModel,
                                              int weight, int priority, String status) {
        ModelAliasDO aliasDO = catalogMapper.selectByAlias(alias).orElse(null);
        if (ObjectUtils.isEmpty(aliasDO)) {
            return null;
        }
        java.time.Instant now = java.time.Instant.now();
        ModelDeploymentDO deploymentDO = catalogMapper.selectDeploymentByKey(deploymentKey)
                .orElseGet(() -> {
                    ModelDeploymentDO created = new ModelDeploymentDO();
                    created.setDeploymentKey(deploymentKey);
                    created.setCreatedAt(now);
                    return created;
                });
        deploymentDO.setApiBase(apiBase);
        if (StringUtils.hasText(apiKeyPlain)) {
            deploymentDO.setApiKeyCipher(secretCipher.encrypt(apiKeyPlain));
        }
        deploymentDO.setUpstreamModel(upstreamModel);
        deploymentDO.setStatus(status);
        deploymentDO.setUpdatedAt(now);
        if (ObjectUtils.isEmpty(deploymentDO.getId())) {
            try {
                catalogMapper.insertDeployment(deploymentDO);
            } catch (DuplicateKeyException ex) {
                // 并发插入同一 deployment_key：唯一约束兜底，转为更新。
                ModelDeploymentDO existing = catalogMapper.selectDeploymentByKey(deploymentKey)
                        .orElseThrow(() -> ex);
                deploymentDO.setId(existing.getId());
                catalogMapper.updateDeployment(deploymentDO);
            }
        } else {
            catalogMapper.updateDeployment(deploymentDO);
        }
        if (catalogMapper.updateAliasDeployment(aliasDO.getId(), deploymentDO.getId(), weight,
                priority) == 0) {
            try {
                catalogMapper.insertAliasDeployment(aliasDO.getId(), deploymentDO.getId(), weight,
                        priority);
            } catch (DuplicateKeyException ex) {
                // 并发建立关联：主键兜底，转为更新权重/优先级。
                catalogMapper.updateAliasDeployment(aliasDO.getId(), deploymentDO.getId(), weight,
                        priority);
            }
        }
        return findAliasAdmin(alias);
    }

    private ModelAliasAdminBO toAdminBO(ModelAliasDO aliasDO) {
        List<ModelDeploymentAdminBO> deployments = catalogMapper
                .selectRowsByAlias(aliasDO.getAlias()).stream()
                .map(row -> new ModelDeploymentAdminBO(row.getDeploymentKey(), row.getApiBase(),
                        row.getUpstreamModel(), row.getWeight(), row.getPriority(),
                        row.getDeploymentStatus()))
                .toList();
        return new ModelAliasAdminBO(aliasDO.getAlias(), aliasDO.getTenantId(),
                aliasDO.getDescription(), readFallbacks(aliasDO.getFallbacks()),
                aliasDO.getStatus(), deployments);
    }

    public ModelCatalogMapper mapper() {
        return catalogMapper;
    }

    public SecretCipher cipher() {
        return secretCipher;
    }
}
