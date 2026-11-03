package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.PromptChatService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.req.PromptChatReqDTO;
import com.qtua.aihub.api.model.req.PromptRenderReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.PromptChatResDTO;
import com.qtua.aihub.api.model.res.PromptRenderResDTO;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.prompt.PromptMessage;
import com.qtua.aihub.integration.prompt.PromptMessageParser;
import com.qtua.aihub.integration.prompt.SimpleTemplateRenderer;
import com.qtua.aihub.manager.model.PromptTemplateBO;
import com.qtua.aihub.manager.prompt.PromptManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Prompt 数据面实现（REQ-2.5）。
 *
 * <p>渲染：查模板（指定版本或最新发布版）→ 变量替换 → 解析 System/User/Assistant 消息。
 * 对话：渲染后在本进程直接调用 {@link ChatCompletionService}（不经 HTTP/Filter，
 * 治理由外层 Filter 统一完成一次）。</p>
 */
@Service
public class PromptChatServiceImpl implements PromptChatService {

    private final PromptManager promptManager;
    private final SimpleTemplateRenderer templateRenderer;
    private final PromptMessageParser messageParser;
    private final ChatCompletionService chatCompletionService;
    private final ObjectMapper objectMapper;

    /**
     * 构造服务。
     *
     * @param promptManager       Prompt 数据门面
     * @param templateRenderer    模板渲染器
     * @param messageParser       消息解析器
     * @param chatCompletionService 聊天补全服务（内部调用）
     * @param objectMapper        JSON 构造器
     */
    public PromptChatServiceImpl(PromptManager promptManager,
                                 SimpleTemplateRenderer templateRenderer,
                                 PromptMessageParser messageParser,
                                 ChatCompletionService chatCompletionService,
                                 ObjectMapper objectMapper) {
        this.promptManager = promptManager;
        this.templateRenderer = templateRenderer;
        this.messageParser = messageParser;
        this.chatCompletionService = chatCompletionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 渲染模板（不调用模型）。
     *
     * @param name   模板名
     * @param reqDTO 渲染参数
     * @return 渲染结果
     */
    @Override
    public PromptRenderResDTO render(String name, PromptRenderReqDTO reqDTO) {
        PromptTemplateBO template = resolve(name, reqDTO.version());
        String rendered = templateRenderer.render(template.content(), reqDTO.variables());
        return new PromptRenderResDTO(name, template.version(), messageParser.parse(rendered));
    }

    /**
     * 渲染并调用模型。
     *
     * @param name      模板名
     * @param reqDTO    对话参数
     * @param auth      认证上下文
     * @param requestId 请求追踪 ID
     * @return 对话结果
     */
    @Override
    public PromptChatResDTO chat(String name, PromptChatReqDTO reqDTO, KeyAuthDTO auth,
                                 String requestId) {
        if (!StringUtils.hasText(reqDTO.model())) {
            throw ChatCompletionException.invalidRequest("model 不能为空", "model");
        }
        PromptTemplateBO template = resolve(name, reqDTO.version());
        String rendered = templateRenderer.render(template.content(), reqDTO.variables());
        List<PromptMessage> messages = messageParser.parse(rendered);
        String requestJson = buildChatRequest(reqDTO, messages);
        ChatCompletionResDTO result = chatCompletionService.chat(
                new ChatCompletionReqDTO(requestJson, auth, requestId));
        return new PromptChatResDTO(result.getRawResponse(), name, template.version(),
                result.getUsage(), result.getDeploymentModel(), result.getServedAlias(),
                result.getRetryCount(), result.isFallbackUsed());
    }

    private PromptTemplateBO resolve(String name, Integer version) {
        if (Objects.nonNull(version)) {
            return promptManager.findByVersion(name, version)
                    .orElseThrow(() -> ChatCompletionException.promptVersionNotFound(name, version));
        }
        return promptManager.findPublished(name)
                .orElseThrow(() -> ChatCompletionException.promptNotFound(name));
    }

    private String buildChatRequest(PromptChatReqDTO reqDTO, List<PromptMessage> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", reqDTO.model());
        body.put("stream", false);
        ArrayNode array = body.putArray("messages");
        for (PromptMessage message : messages) {
            array.addObject().put("role", message.role()).put("content", message.content());
        }
        if (Objects.nonNull(reqDTO.temperature())) {
            body.put("temperature", reqDTO.temperature());
        }
        if (Objects.nonNull(reqDTO.maxTokens())) {
            body.put("max_tokens", reqDTO.maxTokens());
        }
        return body.toString();
    }
}
