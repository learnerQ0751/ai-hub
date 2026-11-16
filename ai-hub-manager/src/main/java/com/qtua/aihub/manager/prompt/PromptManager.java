package com.qtua.aihub.manager.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.PromptTemplateMapper;
import com.qtua.aihub.dal.model.PromptTemplateDO;
import com.qtua.aihub.manager.model.PromptTemplateBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板数据门面。
 *
 * <p>BO↔DO、variables JSON 编解码；版本计算与发布原子操作，不做业务校验。</p>
 */
@Component
public class PromptManager {

    private static final Logger LOG = LoggerFactory.getLogger(PromptManager.class);
    private static final TypeReference<List<String>> VARIABLES_TYPE = new TypeReference<>() {
    };

    private final PromptTemplateMapper promptTemplateMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造门面。
     *
     * @param promptTemplateMapper Prompt 数据访问 Mapper
     * @param objectMapper         JSON 编解码器
     */
    public PromptManager(PromptTemplateMapper promptTemplateMapper, ObjectMapper objectMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存新版本。
     *
     * @param prompt 业务对象
     */
    public void save(PromptTemplateBO prompt) {
        PromptTemplateDO dataObject = new PromptTemplateDO();
        dataObject.setName(prompt.name());
        dataObject.setVersion(prompt.version());
        dataObject.setContent(prompt.content());
        dataObject.setVariables(writeJson(prompt.variables()));
        dataObject.setStatus(prompt.status());
        dataObject.setTenantId(prompt.tenantId());
        dataObject.setCreatedBy(prompt.createdBy());
        dataObject.setCreatedAt(prompt.createdAt());
        promptTemplateMapper.insert(dataObject);
    }

    /**
     * 下一个版本号。
     *
     * @param name 模板名
     * @return 新版本号
     */
    public int nextVersion(String tenantId, String name) {
        Integer max = promptTemplateMapper.selectMaxVersion(tenantId, name);
        return ObjectUtils.isEmpty(max) ? 1 : max + 1;
    }

    /**
     * 最新发布版本。
     *
     * @param name 模板名
     * @return 业务对象；不存在为空
     */
    public Optional<PromptTemplateBO> findPublished(String tenantId, String name) {
        return promptTemplateMapper.selectPublished(tenantId, name).map(this::toBO);
    }

    /**
     * 指定版本。
     *
     * @param name    模板名
     * @param version 版本
     * @return 业务对象；不存在为空
     */
    public Optional<PromptTemplateBO> findByVersion(String tenantId, String name, int version) {
        return promptTemplateMapper.selectByVersion(tenantId, name, version).map(this::toBO);
    }

    /**
     * 全部版本（倒序）。
     *
     * @param name 模板名
     * @return 版本列表
     */
    public List<PromptTemplateBO> findVersions(String tenantId, String name) {
        return promptTemplateMapper.selectVersions(tenantId, name).stream().map(this::toBO)
                .toList();
    }

    /**
     * 全部已发布版本（多模板）。
     *
     * @return 发布版本列表
     */
    public List<PromptTemplateBO> findAllPublished(String tenantId) {
        return promptTemplateMapper.selectAllPublished(tenantId).stream().map(this::toBO).toList();
    }

    /**
     * 锁定同 name 的行（发布事务）。
     *
     * @param name 模板名
     * @return 是否存在
     */
    public boolean lockByName(String tenantId, String name) {
        return !ObjectUtils.isEmpty(promptTemplateMapper.lockByName(tenantId, name));
    }

    /**
     * 归档同 name 的发布版本。
     *
     * @param name 模板名
     * @return 受影响行数
     */
    public int archivePublished(String tenantId, String name) {
        return promptTemplateMapper.archivePublished(tenantId, name);
    }

    /**
     * 发布指定版本。
     *
     * @param name    模板名
     * @param version 版本
     * @return 是否成功
     */
    public boolean publishVersion(String tenantId, String name, int version) {
        return promptTemplateMapper.publishVersion(tenantId, name, version) > 0;
    }

    private PromptTemplateBO toBO(PromptTemplateDO dataObject) {
        return new PromptTemplateBO(
                dataObject.getName(),
                ObjectUtils.isEmpty(dataObject.getVersion()) ? 0 : dataObject.getVersion(),
                dataObject.getContent(),
                readVariables(dataObject.getVariables()),
                dataObject.getStatus(),
                dataObject.getTenantId(),
                dataObject.getCreatedBy(),
                dataObject.getCreatedAt());
    }

    private List<String> readVariables(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return List.of();
        }
        try {
            List<String> variables = objectMapper.readValue(json, VARIABLES_TYPE);
            return ObjectUtils.isEmpty(variables) ? List.of() : variables;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid variables JSON in prompt_templates", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            LOG.error("failed to serialize prompt field", ex);
            throw new IllegalStateException("Failed to serialize prompt field", ex);
        }
    }
}
