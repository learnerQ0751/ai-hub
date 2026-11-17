package com.qtua.aihub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.RagAnswerService;
import com.qtua.aihub.api.RagSearchService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.req.RagAnswerReqDTO;
import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.RagAnswerResDTO;
import com.qtua.aihub.api.model.res.RagCitationResDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;
import com.qtua.aihub.api.model.res.RagUsageResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.tenant.TenantIdentity;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.common.trace.TraceEvent;
import com.qtua.aihub.common.trace.TraceRecorder;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.core.rag.RagAnswerPrompt;
import com.qtua.aihub.core.rag.RagContextAssembler;
import com.qtua.aihub.core.rag.RagContextSegment;
import com.qtua.aihub.core.rag.RagPromptBuilder;
import com.qtua.aihub.core.rag.RagRetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * RAG 生成与引用溯源实现。
 *
 * <p>职责：校验 model、复用检索服务、构造带编号引用的 prompt、内部调用 chat、提取答案与用量、
 * 组装 citations；无召回默认兜底不调模型。全链路日志只记摘要（长度/hash/usage/引用 id），
 * 不记录 prompt 全文与切片正文。本类无可变业务状态，多实例安全。</p>
 */
@Service
public class RagAnswerServiceImpl implements RagAnswerService {

    private static final Logger LOG = LoggerFactory.getLogger(RagAnswerServiceImpl.class);

    private static final int LOG_QUERY_MAX_CHARS = 200;
    private static final int LOG_CITATIONS_MAX = 20;
    private static final int PROMPT_HASH_LENGTH = 16;
    private static final int DEFAULT_TOP_K = 1;
    private static final int TRACE_MAX_ITEMS = 10;
    private static final int HTTP_OK = 200;
    private static final String PARAM_MODEL = "model";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String JSON_MODEL = "model";
    private static final String JSON_STREAM = "stream";
    private static final String JSON_MESSAGES = "messages";
    private static final String JSON_ROLE = "role";
    private static final String JSON_CONTENT = "content";
    private static final String JSON_CHOICES = "choices";
    private static final String JSON_MESSAGE = "message";
    private static final String EMPTY = "";

    private final RagSearchService searchService;
    private final ChatCompletionService chatService;
    private final RagPromptBuilder promptBuilder;
    private final RagContextAssembler contextAssembler;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private final TraceRecorder traceRecorder;

    /**
     * 构造服务（单元测试：无 Trace）。
     *
     * @param searchService    检索服务（复用 REQ-3.3）
     * @param chatService      内部聊天补全服务
     * @param promptBuilder    prompt 构造器
     * @param contextAssembler 上下文拼装器
     * @param objectMapper     JSON 构造/解析器
     * @param properties       RAG 配置
     */
    public RagAnswerServiceImpl(RagSearchService searchService,
                                ChatCompletionService chatService,
                                RagPromptBuilder promptBuilder,
                                RagContextAssembler contextAssembler,
                                ObjectMapper objectMapper,
                                RagProperties properties) {
        this(searchService, chatService, promptBuilder, contextAssembler, objectMapper,
                properties, TraceRecorder.NOOP);
    }

    /**
     * 构造服务（含 Trace）。
     *
     * @param searchService    检索服务
     * @param chatService      内部聊天补全服务
     * @param promptBuilder    prompt 构造器
     * @param contextAssembler 上下文拼装器
     * @param objectMapper     JSON 构造/解析器
     * @param properties       RAG 配置
     * @param traceRecorder    Trace 记录端口
     */
    @org.springframework.beans.factory.annotation.Autowired
    public RagAnswerServiceImpl(RagSearchService searchService,
                                ChatCompletionService chatService,
                                RagPromptBuilder promptBuilder,
                                RagContextAssembler contextAssembler,
                                ObjectMapper objectMapper,
                                RagProperties properties, TraceRecorder traceRecorder) {
        this.searchService = searchService;
        this.chatService = chatService;
        this.promptBuilder = promptBuilder;
        this.contextAssembler = contextAssembler;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.traceRecorder = traceRecorder;
    }

    /**
     * 检索并生成带引用的答案。
     *
     * @param reqDTO    生成请求
     * @param auth      认证上下文
     * @param requestId 请求追踪 ID
     * @return 答案、引用与用量
     */
    @Override
    public RagAnswerResDTO answer(RagAnswerReqDTO reqDTO, KeyAuthDTO auth, String requestId) {
        if (Objects.isNull(reqDTO)) {
            throw ChatCompletionException.invalidRequest("请求体不能为空", null);
        }
        String model = requireModel(reqDTO.model());
        int topK = resolveTopK(reqDTO.topK());
        boolean freeAllowed = properties.getAnswer().isAllowFreeAnswer()
                && Boolean.TRUE.equals(reqDTO.allowFreeAnswer());
        LOG.info("rag answer requested: model={}, projectId={}, query={}, topK={}, "
                        + "freeAnswerAllowed={}",
                model, reqDTO.projectId(), truncate(reqDTO.query()), topK, freeAllowed);
        RagSearchResDTO search = searchService.search(new RagSearchReqDTO(reqDTO.query(),
                reqDTO.projectId(), topK, reqDTO.minScore(), reqDTO.filters(), reqDTO.rerank()));
        String query = search.query().trim();
        if (search.noHit() && !freeAllowed) {
            LOG.info("rag answer no hit: model={}, projectId={}, fallback=true", model,
                    search.projectId());
            return fallbackResponse(query, search, model);
        }
        boolean freeAnswer = search.noHit() && freeAllowed;
        List<RagContextSegment> segments = contextAssembler.assemble(toChunks(search),
                currentTenantId(), search.projectId(), properties.getAnswer().getNeighborWindow());
        RagAnswerPrompt prompt = promptBuilder.buildFromSegments(query, segments,
                properties.getAnswer().getMaxContextChars(), freeAnswer);
        LOG.info("rag answer prompt: model={}, citations={}, promptChars={}, promptHash={}, "
                        + "freeAnswer={}",
                model, prompt.citations().size(), promptChars(prompt), promptHash(prompt),
                freeAnswer);
        ModelResult modelResult = callModel(model, prompt, auth, requestId);
        String answer = extractAnswer(modelResult.rawResponse());
        if (!StringUtils.hasText(answer)) {
            answer = properties.getAnswer().getEmptyAnswerMessage();
            LOG.warn("rag answer empty content: model={}, rawLength={}", model,
                    Objects.isNull(modelResult.rawResponse()) ? 0 : modelResult.rawResponse().length());
        }
        List<RagCitationResDTO> citations = toCitations(prompt);
        LOG.info("rag answer citations: model={}, citations={}", model,
                summarizeCitations(citations));
        TokenUsage usage = modelResult.usage();
        recordAnswerTrace(model, modelResult.deploymentModel(), search.projectId(),
                citations, usage, answer.length());
        return new RagAnswerResDTO(query, search.projectId(), answer, search.noHit(), freeAnswer,
                citations, new RagUsageResDTO(usage.promptTokens(), usage.completionTokens(),
                usage.totalTokens()), model, modelResult.deploymentModel(),
                search.embeddingTokens(), search.embeddingModel());
    }

    /**
     * 记录回答分段（引用 id、模型与用量摘要）。
     *
     * @param model           请求别名
     * @param deploymentModel 实际模型
     * @param projectId       项目
     * @param citations       引用
     * @param usage           用量
     * @param answerChars     答案长度
     */
    private void recordAnswerTrace(String model, String deploymentModel, String projectId,
                                   List<RagCitationResDTO> citations, TokenUsage usage,
                                   int answerChars) {
        try {
            TenantIdentity identity = com.qtua.aihub.common.tenant.TenantContextHolder.current();
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("model", model);
            payload.put("deployment", deploymentModel);
            payload.put("citations", citations.stream().limit(TRACE_MAX_ITEMS)
                    .map(RagCitationResDTO::chunkId).toList());
            payload.put("promptTokens", usage.promptTokens());
            payload.put("completionTokens", usage.completionTokens());
            payload.put("answerChars", answerChars);
            String traceId = org.slf4j.MDC.get("trace_id");
            String effectiveTraceId = ObjectUtils.isEmpty(traceId) ? "-" : traceId;
            String tenant = ObjectUtils.isEmpty(identity) || !StringUtils.hasText(identity.tenantId())
                    ? "default" : identity.tenantId();
            String keyHash = ObjectUtils.isEmpty(identity) ? null : identity.keyHash();
            traceRecorder.record(new TraceEvent(effectiveTraceId, tenant, projectId, keyHash,
                    "model", model, HTTP_OK, 0L, payload, java.time.Instant.now()));
            traceRecorder.record(new TraceEvent(effectiveTraceId, tenant, projectId, keyHash,
                    "answer", model, HTTP_OK, 0L, payload, java.time.Instant.now()));
        } catch (RuntimeException ex) {
            // Trace 失败不影响回答。
        }
    }

    /**
     * 当前租户：与检索的租户口径一致（虚拟 Key 用绑定租户，无身份/管理员用默认租户）。
     *
     * @return 租户 ID
     */
    private String currentTenantId() {
        TenantIdentity identity = com.qtua.aihub.common.tenant.TenantContextHolder.current();
        if (ObjectUtils.isEmpty(identity) || identity.master()
                || !StringUtils.hasText(identity.tenantId())) {
            return TenantScopeGuard.DEFAULT_TENANT;
        }
        return identity.tenantId();
    }

    private String requireModel(String model) {
        if (!StringUtils.hasText(model)) {
            throw ChatCompletionException.invalidRequest("model 不能为空", PARAM_MODEL);
        }
        return model.trim();
    }

    private int resolveTopK(Integer topK) {
        if (!ObjectUtils.isEmpty(topK)) {
            return topK;
        }
        return Math.max(DEFAULT_TOP_K, properties.getAnswer().getDefaultTopK());
    }

    private RagAnswerResDTO fallbackResponse(String query, RagSearchResDTO search, String model) {
        return new RagAnswerResDTO(query, search.projectId(),
                properties.getAnswer().getFallbackMessage(), true, false, List.of(),
                new RagUsageResDTO(0, 0, 0), model, null,
                search.embeddingTokens(), search.embeddingModel());
    }

    private ModelResult callModel(String model, RagAnswerPrompt prompt, KeyAuthDTO auth,
                                  String requestId) {
        long startedAt = System.currentTimeMillis();
        ChatCompletionResDTO result = chatService.chat(
                new ChatCompletionReqDTO(buildChatRequest(model, prompt), auth, requestId));
        TokenUsage usage = Objects.isNull(result.getUsage()) ? TokenUsage.ZERO : result.getUsage();
        LOG.info("rag answer model completed: model={}, deploymentModel={}, usage={}, latencyMs={}, "
                        + "answerLength={}",
                model, result.getDeploymentModel(), usage,
                Math.max(0L, System.currentTimeMillis() - startedAt),
                extractAnswer(result.getRawResponse()).length());
        return new ModelResult(result.getRawResponse(), usage, result.getDeploymentModel());
    }

    private String buildChatRequest(String model, RagAnswerPrompt prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put(JSON_MODEL, model);
        body.put(JSON_STREAM, false);
        ArrayNode messages = body.putArray(JSON_MESSAGES);
        messages.addObject().put(JSON_ROLE, ROLE_SYSTEM).put(JSON_CONTENT, prompt.systemPrompt());
        messages.addObject().put(JSON_ROLE, ROLE_USER).put(JSON_CONTENT, prompt.userPrompt());
        return body.toString();
    }

    private String extractAnswer(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return EMPTY;
        }
        try {
            JsonNode content = objectMapper.readTree(rawResponse)
                    .path(JSON_CHOICES).path(0).path(JSON_MESSAGE).path(JSON_CONTENT);
            return content.isTextual() ? content.asText() : EMPTY;
        } catch (JsonProcessingException ex) {
            LOG.warn("rag answer response parse failed: length={}",
                    Objects.isNull(rawResponse) ? 0 : rawResponse.length());
            return EMPTY;
        }
    }

    private List<RagRetrievedChunk> toChunks(RagSearchResDTO search) {
        return search.results().stream()
                .map(hit -> new RagRetrievedChunk(hit.chunkId(), hit.docId(),
                        search.projectId(), hit.source(), hit.chunkIndex(), hit.content(),
                        hit.score(), hit.rerankScore()))
                .toList();
    }

    private List<RagCitationResDTO> toCitations(RagAnswerPrompt prompt) {
        return prompt.citations().stream()
                .map(citation -> new RagCitationResDTO(citation.index(), citation.docId(),
                        citation.chunkId(), citation.source(), citation.score()))
                .toList();
    }

    private String summarizeCitations(List<RagCitationResDTO> citations) {
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(citations.size(), LOG_CITATIONS_MAX);
        for (int index = 0; index < limit; index++) {
            RagCitationResDTO citation = citations.get(index);
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(citation.index()).append(':').append(citation.chunkId());
        }
        if (citations.size() > LOG_CITATIONS_MAX) {
            builder.append(", ... total=").append(citations.size());
        }
        return builder.append(']').toString();
    }

    private int promptChars(RagAnswerPrompt prompt) {
        return prompt.systemPrompt().length() + prompt.userPrompt().length();
    }

    private String promptHash(RagAnswerPrompt prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(
                    (prompt.systemPrompt() + prompt.userPrompt()).getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, PROMPT_HASH_LENGTH);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }

    private String truncate(String query) {
        if (Objects.isNull(query)) {
            return null;
        }
        return query.length() > LOG_QUERY_MAX_CHARS
                ? query.substring(0, LOG_QUERY_MAX_CHARS) + "..." : query;
    }

    /**
     * 一次模型调用的不可变结果（避免用成员变量承载请求级用量）。
     *
     * @param rawResponse     上游原始响应
     * @param usage           Token 用量
     * @param deploymentModel 实际部署模型
     */
    private record ModelResult(String rawResponse, TokenUsage usage, String deploymentModel) {
    }
}
