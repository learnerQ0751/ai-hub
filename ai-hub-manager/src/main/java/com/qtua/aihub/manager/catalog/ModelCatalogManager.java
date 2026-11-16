package com.qtua.aihub.manager.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.crypto.SecretCipher;
import com.qtua.aihub.dal.mapper.ModelCatalogMapper;
import com.qtua.aihub.dal.model.ModelCatalogRow;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

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

    public ModelCatalogMapper mapper() {
        return catalogMapper;
    }

    public SecretCipher cipher() {
        return secretCipher;
    }
}
