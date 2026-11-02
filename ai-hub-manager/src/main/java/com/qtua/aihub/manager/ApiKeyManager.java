package com.qtua.aihub.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.ApiKeyMapper;
import com.qtua.aihub.dal.model.ApiKeyDO;
import com.qtua.aihub.manager.model.ApiKeyBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 虚拟 Key 数据门面。
 *
 * <p>负责 BO↔DO 与 JSON 字段编解码，不做业务判断；业务规则在 service/core。</p>
 */
@Component
public class ApiKeyManager {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyManager.class);
    private static final TypeReference<List<String>> MODEL_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final ApiKeyMapper apiKeyMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造数据门面。
     *
     * @param apiKeyMapper Key 数据访问 Mapper
     * @param objectMapper JSON 编解码器
     */
    public ApiKeyManager(ApiKeyMapper apiKeyMapper, ObjectMapper objectMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存 Key。
     *
     * @param apiKey 业务对象
     */
    public void save(ApiKeyBO apiKey) {
        ApiKeyDO dataObject = new ApiKeyDO();
        dataObject.setKeyHash(apiKey.getKeyHash());
        dataObject.setModels(writeJson(apiKey.getModels()));
        dataObject.setSpend(apiKey.getSpend());
        dataObject.setMaxBudget(apiKey.getMaxBudget());
        dataObject.setRpmLimit(apiKey.getRpmLimit());
        dataObject.setTpmLimit(apiKey.getTpmLimit());
        dataObject.setExpiresAt(apiKey.getExpiresAt());
        dataObject.setBlocked(apiKey.isBlocked());
        dataObject.setMetadata(writeJson(apiKey.getMetadata()));
        dataObject.setCreatedAt(apiKey.getCreatedAt());
        apiKeyMapper.insert(dataObject);
    }

    /**
     * 按哈希查询 Key。
     *
     * @param keyHash SHA-256 摘要
     * @return 业务对象；不存在时为空
     */
    public Optional<ApiKeyBO> findByKeyHash(String keyHash) {
        return apiKeyMapper.selectByKeyHash(keyHash).map(this::toBO);
    }

    /**
     * 更新封禁状态。
     *
     * @param keyHash SHA-256 摘要
     * @param blocked 是否封禁
     * @return 是否更新成功
     */
    public boolean updateBlocked(String keyHash, boolean blocked) {
        return apiKeyMapper.updateBlocked(keyHash, blocked) > 0;
    }

    private ApiKeyBO toBO(ApiKeyDO dataObject) {
        return new ApiKeyBO(
                dataObject.getKeyHash(),
                readModels(dataObject.getModels()),
                dataObject.getSpend(),
                dataObject.getMaxBudget(),
                dataObject.getRpmLimit(),
                dataObject.getTpmLimit(),
                dataObject.getExpiresAt(),
                dataObject.isBlocked(),
                readMetadata(dataObject.getMetadata()),
                dataObject.getCreatedAt());
    }

    private List<String> readModels(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return List.of();
        }
        try {
            List<String> models = objectMapper.readValue(json, MODEL_LIST_TYPE);
            return ObjectUtils.isEmpty(models) ? List.of() : models;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid models JSON in api_keys", ex);
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid metadata JSON in api_keys", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            // 序列化失败属于内部错误，记录后抛出，避免写入脏数据。
            LOG.error("failed to serialize api key field", ex);
            throw new IllegalStateException("Failed to serialize api key field", ex);
        }
    }
}
