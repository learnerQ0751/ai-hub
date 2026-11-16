package com.qtua.aihub.service;

import com.qtua.aihub.api.PromptService;
import com.qtua.aihub.api.model.req.PromptCreateReqDTO;
import com.qtua.aihub.api.model.req.PromptVersionCreateReqDTO;
import com.qtua.aihub.api.model.res.PromptListResDTO;
import com.qtua.aihub.api.model.res.PromptResDTO;
import com.qtua.aihub.api.model.res.PromptVersionListResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.prompt.PromptStatus;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.integration.prompt.SimpleTemplateRenderer;
import com.qtua.aihub.manager.model.PromptTemplateBO;
import com.qtua.aihub.manager.prompt.PromptManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Prompt 模板管理实现。
 *
 * <p>版本只追加；发布事务内锁定同 name 行，旧 PUBLISHED 归档后发布目标版本。
 * 变量声明必须与模板占位符一致；内容做长度与疑似密钥校验。</p>
 */
@Service
public class PromptServiceImpl implements PromptService {

    private static final Logger LOG = LoggerFactory.getLogger(PromptServiceImpl.class);
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern SECRET_PATTERN = Pattern.compile("sk-[A-Za-z0-9_-]{16,}");
    private static final int CONTENT_MAX_LENGTH = 10000;

    private final PromptManager promptManager;
    private final SimpleTemplateRenderer templateRenderer;
    private final TenantScopeGuard tenantScopeGuard;

    /**
     * 兼容构造器（单元测试）：使用无上下文的作用域守卫。
     *
     * @param promptManager    Prompt 数据门面
     * @param templateRenderer 模板校验/渲染器
     */
    public PromptServiceImpl(PromptManager promptManager,
                             SimpleTemplateRenderer templateRenderer) {
        this(promptManager, templateRenderer, new TenantScopeGuard());
    }

    /**
     * 构造服务。
     *
     * @param promptManager    Prompt 数据门面
     * @param templateRenderer 模板校验/渲染器
     * @param tenantScopeGuard 租户作用域守卫
     */
    @org.springframework.beans.factory.annotation.Autowired
    public PromptServiceImpl(PromptManager promptManager, SimpleTemplateRenderer templateRenderer,
                             TenantScopeGuard tenantScopeGuard) {
        this.promptManager = promptManager;
        this.templateRenderer = templateRenderer;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    /**
     * 创建模板 v1（DRAFT）。
     *
     * @param reqDTO 创建参数
     * @return 创建的版本
     */
    @Override
    public PromptResDTO create(PromptCreateReqDTO reqDTO) {
        String name = validateName(reqDTO.name());
        String tenantId = tenantScopeGuard.resolveTenant(reqDTO.tenantId());
        validateContentAndVariables(reqDTO.content(), reqDTO.variables());
        if (!promptManager.findVersions(tenantId, name).isEmpty()) {
            throw ChatCompletionException.invalidRequest(
                    "Prompt '" + name + "' 已存在，请使用追加版本接口", "name");
        }
        PromptTemplateBO prompt = new PromptTemplateBO(name,
                promptManager.nextVersion(tenantId, name),
                reqDTO.content(), reqDTO.variables(), PromptStatus.DRAFT.name(),
                tenantId, reqDTO.createdBy(), Instant.now());
        promptManager.save(prompt);
        LOG.info("prompt created: name={}, version={}", name, prompt.version());
        return toResDTO(prompt);
    }

    /**
     * 追加新版本（DRAFT）。
     *
     * @param name   模板名
     * @param reqDTO 版本参数
     * @return 新版本
     */
    @Override
    public PromptResDTO createVersion(String name, PromptVersionCreateReqDTO reqDTO) {
        String tenantId = tenantScopeGuard.resolveTenant(null);
        List<PromptTemplateBO> versions = promptManager.findVersions(tenantId, name);
        if (versions.isEmpty()) {
            throw ChatCompletionException.promptNotFound(name);
        }
        validateContentAndVariables(reqDTO.content(), reqDTO.variables());
        PromptTemplateBO latest = versions.get(0);
        PromptTemplateBO prompt = new PromptTemplateBO(name,
                promptManager.nextVersion(tenantId, name),
                reqDTO.content(), reqDTO.variables(), PromptStatus.DRAFT.name(),
                latest.tenantId(), latest.createdBy(), Instant.now());
        promptManager.save(prompt);
        LOG.info("prompt version created: name={}, version={}", name, prompt.version());
        return toResDTO(prompt);
    }

    /**
     * 查询模板。
     *
     * @param name    模板名
     * @param version 版本（null 表示最新发布版）
     * @return 版本视图
     */
    @Override
    public PromptResDTO get(String name, Integer version) {
        String tenantId = tenantScopeGuard.resolveTenant(null);
        if (Objects.nonNull(version)) {
            return promptManager.findByVersion(tenantId, name, version)
                    .map(this::toResDTO)
                    .orElseThrow(() -> ChatCompletionException.promptVersionNotFound(name, version));
        }
        return promptManager.findPublished(tenantId, name)
                .map(this::toResDTO)
                .orElseThrow(() -> ChatCompletionException.promptNotFound(name));
    }

    /**
     * 版本历史。
     *
     * @param name 模板名
     * @return 版本列表
     */
    @Override
    public PromptVersionListResDTO versions(String name) {
        String tenantId = tenantScopeGuard.resolveTenant(null);
        List<PromptTemplateBO> versions = promptManager.findVersions(tenantId, name);
        if (versions.isEmpty()) {
            throw ChatCompletionException.promptNotFound(name);
        }
        return new PromptVersionListResDTO(versions.stream().map(this::toResDTO).toList());
    }

    /**
     * 模板列表（每个模板的最新发布版本）。
     *
     * @param tenantId 租户过滤（可空）
     * @return 模板列表
     */
    @Override
    public PromptListResDTO list(String tenantId) {
        String effectiveTenant = tenantScopeGuard.resolveTenant(tenantId);
        List<PromptResDTO> prompts = promptManager.findAllPublished(effectiveTenant).stream()
                .map(this::toResDTO)
                .toList();
        return new PromptListResDTO(prompts);
    }

    /**
     * 发布指定版本（发布旧版本即回滚）。
     *
     * @param name    模板名
     * @param version 目标版本
     * @return 发布后的版本
     */
    @Override
    @Transactional
    public PromptResDTO publish(String name, int version) {
        String tenantId = tenantScopeGuard.resolveTenant(null);
        if (!promptManager.lockByName(tenantId, name)) {
            throw ChatCompletionException.promptNotFound(name);
        }
        PromptTemplateBO target = promptManager.findByVersion(tenantId, name, version)
                .orElseThrow(() -> ChatCompletionException.promptVersionNotFound(name, version));
        promptManager.archivePublished(tenantId, name);
        promptManager.publishVersion(tenantId, name, version);
        LOG.info("prompt published: name={}, version={}", name, version);
        return toResDTO(new PromptTemplateBO(target.name(), target.version(), target.content(),
                target.variables(), PromptStatus.PUBLISHED.name(), target.tenantId(),
                target.createdBy(), target.createdAt()));
    }

    private String validateName(String name) {
        if (!StringUtils.hasText(name) || !NAME_PATTERN.matcher(name).matches()) {
            throw ChatCompletionException.invalidRequest(
                    "Prompt 名称必须为 1-128 位字母、数字、下划线或中划线", "name");
        }
        return name;
    }

    private void validateContentAndVariables(String content, List<String> variables) {
        if (!StringUtils.hasText(content)) {
            throw ChatCompletionException.invalidRequest("模板内容不能为空", "content");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw ChatCompletionException.invalidRequest(
                    "模板内容不能超过 " + CONTENT_MAX_LENGTH + " 个字符", "content");
        }
        if (SECRET_PATTERN.matcher(content).find()) {
            throw ChatCompletionException.invalidRequest(
                    "模板内容疑似包含密钥（sk- 开头长串），禁止写入", "content");
        }
        for (String variable : variables) {
            if (!StringUtils.hasText(variable) || !VARIABLE_PATTERN.matcher(variable).matches()) {
                throw ChatCompletionException.invalidRequest(
                        "变量名必须为 1-64 位字母、数字或下划线：" + variable, "variables");
            }
        }
        templateRenderer.validateDeclaration(content, variables);
    }

    private PromptResDTO toResDTO(PromptTemplateBO prompt) {
        return new PromptResDTO(prompt.name(), prompt.version(), prompt.status(),
                prompt.content(), prompt.variables(), prompt.tenantId(),
                prompt.createdBy(), prompt.createdAt());
    }
}
