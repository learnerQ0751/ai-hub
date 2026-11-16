package com.qtua.aihub.service.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.RateLimitProperties;
import com.qtua.aihub.common.constant.JsonFields;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenEstimate;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.pricing.ModelPrice;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingModelProvider;
import com.qtua.aihub.common.tenant.TenantScopeGuard;
import com.qtua.aihub.common.trace.TraceEvent;
import com.qtua.aihub.common.trace.TraceRecorder;
import com.qtua.aihub.common.metering.EmbeddingTokenEstimator;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.CallLogManager;
import com.qtua.aihub.manager.model.CallLogBO;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import com.qtua.aihub.service.gateway.model.GuardRequest;
import com.qtua.aihub.service.gateway.model.GuardReservation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 网关治理公共实现。
 *
 * <p>与具体业务接口无关：从请求中提取模型/Token 估算，执行限流与预算预占；
 * 业务成功后按实际 usage 计价、结算、累计 spend、写访问日志；失败释放并记录。
 * 新增流式/文件接口时，只需扩展请求提取器或回填 usage，治理流程复用。</p>
 */
@Service
public class GatewayGovernanceService {

    private static final String JSON_INPUT = "input";
    private static final String TARGET_RAG_ANSWER = "rag_answer";

    private final ModelCatalog modelCatalog;
    private final GatewayGuard gatewayGuard;
    private final PricingCatalog pricingCatalog;
    private final CallLogManager callLogManager;
    private final OpenAiProtocolAdapter protocolAdapter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final EmbeddingModelProvider embeddingModelProvider;
    private final TraceRecorder traceRecorder;

    /**
     * 构造治理服务。
     *
     * @param modelCatalog         模型目录
     * @param gatewayGuard         限流与预算端口
     * @param pricingCatalog       价格目录
     * @param callLogManager       调用日志与消费累计门面
     * @param protocolAdapter      OpenAI 协议适配器（Token 估算）
     * @param rateLimitProperties  限流配置
     * @param objectMapper         JSON 解析器
     * @param embeddingModelProvider embedding 模型目录
     */
    public GatewayGovernanceService(ModelCatalog modelCatalog,
                                    GatewayGuard gatewayGuard,
                                    PricingCatalog pricingCatalog,
                                    CallLogManager callLogManager,
                                    OpenAiProtocolAdapter protocolAdapter,
                                    RateLimitProperties rateLimitProperties,
                                    ObjectMapper objectMapper,
                                    EmbeddingModelProvider embeddingModelProvider) {
        this(modelCatalog, gatewayGuard, pricingCatalog, callLogManager, protocolAdapter,
                rateLimitProperties, objectMapper, embeddingModelProvider, TraceRecorder.NOOP);
    }

    /**
     * 构造治理服务（含 Trace）。
     *
     * @param modelCatalog          模型目录
     * @param gatewayGuard          限流与预算端口
     * @param pricingCatalog        价格目录
     * @param callLogManager        调用日志门面
     * @param protocolAdapter       OpenAI 协议适配器
     * @param rateLimitProperties   限流配置
     * @param objectMapper          JSON 解析器
     * @param embeddingModelProvider embedding 模型目录
     * @param traceRecorder         Trace 记录端口
     */
    @org.springframework.beans.factory.annotation.Autowired
    public GatewayGovernanceService(ModelCatalog modelCatalog,
                                    GatewayGuard gatewayGuard,
                                    PricingCatalog pricingCatalog,
                                    CallLogManager callLogManager,
                                    OpenAiProtocolAdapter protocolAdapter,
                                    RateLimitProperties rateLimitProperties,
                                    ObjectMapper objectMapper,
                                    EmbeddingModelProvider embeddingModelProvider,
                                    TraceRecorder traceRecorder) {
        this.modelCatalog = modelCatalog;
        this.gatewayGuard = gatewayGuard;
        this.pricingCatalog = pricingCatalog;
        this.callLogManager = callLogManager;
        this.protocolAdapter = protocolAdapter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
        this.embeddingModelProvider = embeddingModelProvider;
        this.traceRecorder = traceRecorder;
    }

    /**
     * 请求前预检查与预占。
     *
     * <p>只做网关治理需要的解析：model、白名单、部署存在性、Token 估算；
     * 业务参数校验仍由业务服务负责。</p>
     *
     * @param context     治理上下文
     * @param requestBody 请求体
     */
    public void reserve(GatewayContext context, String requestBody) {
        if (context.isEmbeddingCall()) {
            reserveEmbedding(context, requestBody);
            return;
        }
        if (context.isToolCall()) {
            reserveToolCall(context);
            return;
        }
        JsonNode root = parseQuietly(requestBody);
        if (ObjectUtils.isEmpty(root)) {
            return;
        }
        JsonNode streamNode = root.get(JsonFields.STREAM);
        context.setStreaming(!ObjectUtils.isEmpty(streamNode) && streamNode.asBoolean(false));
        JsonNode modelNode = root.get(JsonFields.MODEL);
        if (ObjectUtils.isEmpty(modelNode) || modelNode.isNull()
                || !StringUtils.hasText(modelNode.asText())) {
            return;
        }
        String alias = modelNode.asText();
        context.setModelAlias(alias);
        assertModelAccess(context.getAuth(), alias);
        List<ModelDeployment> deployments = modelCatalog.findDeployments(alias);
        if (ObjectUtils.isEmpty(deployments)) {
            throw ChatCompletionException.invalidModel(alias);
        }
        TokenEstimate estimate = protocolAdapter.estimateTokens(
                requestBody, rateLimitProperties.getDefaultMaxTokens());
        BigDecimal estimatedCost = estimateCost(deployments, estimate);
        context.setEstimate(estimate.totalTokens(), estimatedCost);
        KeyAuthDTO auth = context.getAuth();
        GuardRequest request = new GuardRequest(context.getRequestId(), context.getKeyHash(), alias,
                auth.rpmLimit(), auth.tpmLimit(), auth.maxBudget(), auth.budgetDuration(),
                estimate.totalTokens(), estimatedCost);
        context.setReservation(gatewayGuard.reserve(request));
    }

    /**
     * embedding 调用预占：解析别名、估算输入 token、按模型单价预估成本。
     *
     * @param context     治理上下文
     * @param requestBody 请求体
     */
    private void reserveEmbedding(GatewayContext context, String requestBody) {
        JsonNode root = parseQuietly(requestBody);
        if (ObjectUtils.isEmpty(root)) {
            return;
        }
        JsonNode modelNode = root.get(JsonFields.MODEL);
        if (ObjectUtils.isEmpty(modelNode) || !StringUtils.hasText(modelNode.asText())) {
            return;
        }
        String alias = modelNode.asText();
        context.setModelAlias(alias);
        assertModelAccess(context.getAuth(), alias);
        EmbeddingModel model = embeddingModelProvider.findByAlias(alias)
                .orElseThrow(() -> ChatCompletionException.invalidModel(alias));
        long estimatedTokens = estimateEmbeddingTokens(root.get(JSON_INPUT));
        BigDecimal estimatedCost = costOfEmbedding(model, estimatedTokens);
        context.setEstimate(estimatedTokens, estimatedCost);
        KeyAuthDTO auth = context.getAuth();
        GuardRequest request = new GuardRequest(context.getRequestId(), context.getKeyHash(), alias,
                auth.rpmLimit(), auth.tpmLimit(), auth.maxBudget(), auth.budgetDuration(),
                estimatedTokens, estimatedCost);
        context.setReservation(gatewayGuard.reserve(request));
    }

    /**
     * 估算 embedding 输入 token：字符串或字符串数组。
     *
     * @param inputNode input 节点
     * @return token 估算
     */
    private long estimateEmbeddingTokens(JsonNode inputNode) {
        if (ObjectUtils.isEmpty(inputNode) || inputNode.isNull()) {
            return 0L;
        }
        if (inputNode.isTextual()) {
            return EmbeddingTokenEstimator.estimate(inputNode.asText());
        }
        if (inputNode.isArray()) {
            long total = 0L;
            for (JsonNode item : inputNode) {
                if (item.isTextual()) {
                    total += EmbeddingTokenEstimator.estimate(item.asText());
                }
            }
            return total;
        }
        return 0L;
    }

    /**
     * 工具类调用预占（MCP / 记忆 / RAG）：只做 Key 级 RPM，TPM/预算不适用。
     *
     * @param context 治理上下文
     */
    private void reserveToolCall(GatewayContext context) {
        KeyAuthDTO auth = context.getAuth();
        // 用工具名/操作名作为 RPM 的 model 维度，日志 model_alias 也记录同一目标。
        context.setModelAlias(context.getTargetName());
        GuardRequest request = new GuardRequest(context.getRequestId(), context.getKeyHash(),
                context.getTargetName(), auth.rpmLimit(), null, null, null, 0, BigDecimal.ZERO);
        context.setReservation(gatewayGuard.reserve(request));
    }

    /**
     * 成功路径：按实际用量计价、结算、累计 spend、写日志。
     *
     * @param context 治理上下文
     */
    public void complete(GatewayContext context) {
        if (ObjectUtils.isEmpty(context)) {
            return;
        }
        if (context.isEmbeddingCall()) {
            completeEmbedding(context);
            return;
        }
        if (context.isRagCall()) {
            completeRagCall(context);
            return;
        }
        if (context.isToolCall()) {
            completeToolCall(context);
            return;
        }
        if (!context.hasModelAlias()) {
            return;
        }
        BigDecimal cost = calculateCost(context.getDeploymentModel(), context.getUsage());
        GuardReservation reservation = context.getReservation();
        if (Objects.nonNull(reservation)) {
            gatewayGuard.settle(reservation, context.getUsage(), cost);
        }
        callLogManager.addSpend(context.getKeyHash(), cost);
        recordLog(context, context.getUsage(), cost, HttpStatus.OK.value(), null);
    }

    /**
     * embedding 调用收尾：按实际 usage 与模型单价结算、累计 spend 并写日志。
     *
     * @param context 治理上下文
     */
    private void completeEmbedding(GatewayContext context) {
        TokenUsage usage = context.getUsage();
        EmbeddingModel model = embeddingModelProvider.findByAlias(context.getModelAlias())
                .orElse(null);
        BigDecimal cost = Objects.isNull(model) ? BigDecimal.ZERO
                : costOfEmbedding(model, usage.promptTokens());
        if (Objects.nonNull(model)) {
            // deployment_model 记录上游真实模型名，便于审计与复算。
            context.complete(usage, model.modelId());
        }
        GuardReservation reservation = context.getReservation();
        if (Objects.nonNull(reservation)) {
            gatewayGuard.settle(reservation, usage, cost);
        }
        callLogManager.addSpend(context.getKeyHash(), cost);
        recordLog(context, usage, cost, HttpStatus.OK.value(), null);
    }

    /**
     * RAG 调用收尾：answer 按 chat + embedding 实际成本计价并事后校验预算；
     * 检索/文档只记录 embedding 成本，不做预算扣减。
     *
     * @param context 治理上下文
     */
    private void completeRagCall(GatewayContext context) {
        boolean answer = TARGET_RAG_ANSWER.equals(context.getTargetName());
        BigDecimal chatCost = answer
                ? calculateCost(context.getDeploymentModel(), context.getUsage())
                : BigDecimal.ZERO;
        BigDecimal embeddingCost = calculateEmbeddingCost(context);
        BigDecimal totalCost = chatCost.add(embeddingCost);
        GuardReservation reservation = context.getReservation();
        if (Objects.nonNull(reservation)) {
            gatewayGuard.settle(reservation, answer ? context.getUsage() : TokenUsage.ZERO,
                    totalCost);
        }
        String errorType = null;
        if (answer) {
            KeyAuthDTO auth = context.getAuth();
            boolean withinBudget = gatewayGuard.chargeAfterwards(context.getKeyHash(),
                    auth.budgetDuration(), auth.maxBudget(), totalCost);
            if (!withinBudget) {
                errorType = "budget_exceeded";
            }
            callLogManager.addSpend(context.getKeyHash(), totalCost);
        }
        recordLog(context, answer ? context.getUsage() : TokenUsage.ZERO, totalCost,
                HttpStatus.OK.value(), errorType);
    }

    /**
     * 计算上下文中的辅助 embedding 成本。
     *
     * @param context 治理上下文
     * @return 成本；无 embedding 用量为 0
     */
    private BigDecimal calculateEmbeddingCost(GatewayContext context) {
        if (context.getEmbeddingTokens() <= 0L
                || !StringUtils.hasText(context.getEmbeddingModel())) {
            return BigDecimal.ZERO;
        }
        return embeddingModelProvider.findByModelId(context.getEmbeddingModel())
                .map(model -> costOfEmbedding(model, context.getEmbeddingTokens()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 按 embedding 模型单价计算成本，空值兜底为 0。
     *
     * @param model       模型
     * @param inputTokens 输入 token
     * @return 成本
     */
    private BigDecimal costOfEmbedding(EmbeddingModel model, long inputTokens) {
        if (inputTokens <= 0L) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = model.costOfInputTokens(inputTokens);
        return Objects.isNull(cost) ? BigDecimal.ZERO : cost;
    }

    /**
     * 工具类调用成功收尾（MCP / 记忆 / RAG）：结算 RPM 预占并写日志（cost=0）。
     *
     * @param context 治理上下文
     */
    private void completeToolCall(GatewayContext context) {
        GuardReservation reservation = context.getReservation();
        if (Objects.nonNull(reservation)) {
            gatewayGuard.settle(reservation, TokenUsage.ZERO, BigDecimal.ZERO);
        }
        recordLog(context, TokenUsage.ZERO, BigDecimal.ZERO, HttpStatus.OK.value(), null);
    }

    /**
     * 失败路径：释放预占并记录失败日志。
     *
     * @param context 治理上下文
     */
    public void fail(GatewayContext context) {
        if (ObjectUtils.isEmpty(context)) {
            return;
        }
        GuardReservation reservation = context.getReservation();
        if (Objects.nonNull(reservation)) {
            gatewayGuard.release(reservation);
        }
        if (context.isToolCall() || context.hasModelAlias()) {
            recordLog(context, TokenUsage.ZERO, BigDecimal.ZERO,
                    context.getStatus(), context.getErrorType());
        }
    }

    /**
     * 校验模型白名单。
     *
     * @param auth  认证上下文
     * @param alias 模型别名
     */
    private void assertModelAccess(KeyAuthDTO auth, String alias) {
        if (auth.masterKey() || auth.models().isEmpty() || auth.models().contains(alias)) {
            return;
        }
        throw ChatCompletionException.keyModelAccessDenied(alias);
    }

    /**
     * 估算预算成本：取别名下部署的最高单价，保守预占。
     *
     * @param deployments 部署列表
     * @param estimate    Token 估算
     * @return 估算成本
     */
    private BigDecimal estimateCost(List<ModelDeployment> deployments, TokenEstimate estimate) {
        BigDecimal max = BigDecimal.ZERO;
        TokenUsage usage = new TokenUsage(estimate.inputTokens(),
                estimate.maxOutputTokens(), estimate.totalTokens());
        for (ModelDeployment deployment : deployments) {
            Optional<ModelPrice> price = pricingCatalog.findPrice(deployment.upstreamModel());
            if (price.isPresent()) {
                BigDecimal cost = price.get().costOf(usage);
                if (cost.compareTo(max) > 0) {
                    max = cost;
                }
            }
        }
        return max;
    }

    /**
     * 按实际部署与用量计算成本。
     *
     * @param deploymentModel 实际部署模型
     * @param usage           实际用量
     * @return 实际成本
     */
    private BigDecimal calculateCost(String deploymentModel, TokenUsage usage) {
        if (!StringUtils.hasText(deploymentModel)) {
            return BigDecimal.ZERO;
        }
        return pricingCatalog.findPrice(deploymentModel)
                .map(price -> price.costOf(usage))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 记录调用日志；异常不影响响应。
     *
     * @param context       治理上下文
     * @param usage         Token 用量
     * @param cost          成本
     * @param status        HTTP 状态码
     * @param errorType     错误类型
     */
    private void recordLog(GatewayContext context, TokenUsage usage, BigDecimal cost,
                           int status, String errorType) {
        try {
            callLogManager.record(new CallLogBO(context.getRequestId(), context.getKeyHash(),
                    context.getModelAlias(), context.getDeploymentModel(), usage, cost,
                    latencyMs(context), status, errorType, tenantOf(context), Instant.now(),
                    context.getServedAlias(), context.getRetryCount(),
                    context.isFallbackUsed(), context.getCallType(), context.getTargetName(),
                    ObjectUtils.isEmpty(context.getAuth()) ? null : context.getAuth().projectId()));
        } catch (RuntimeException ex) {
            // 日志失败不影响业务响应。
        }
        if (!context.isToolCall()) {
            recordTrace(context, usage, cost, status, errorType);
        }
    }

    /**
     * 记录模型段 Trace（chat/embedding/prompt）。
     *
     * @param context   治理上下文
     * @param usage     用量
     * @param cost      成本
     * @param status    状态
     * @param errorType 错误类型
     */
    private void recordTrace(GatewayContext context, TokenUsage usage, BigDecimal cost,
                             int status, String errorType) {
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("alias", context.getModelAlias());
            payload.put("deployment", context.getDeploymentModel());
            payload.put("promptTokens", usage.promptTokens());
            payload.put("completionTokens", usage.completionTokens());
            payload.put("cost", cost);
            payload.put("errorType", errorType);
            traceRecorder.record(new TraceEvent(context.getRequestId(), tenantOf(context),
                    projectOf(context), context.getKeyHash(), "model", context.getModelAlias(),
                    status, latencyMs(context), payload, Instant.now()));
        } catch (RuntimeException ex) {
            // Trace 失败不影响业务响应。
        }
    }

    private String projectOf(GatewayContext context) {
        return ObjectUtils.isEmpty(context.getAuth()) ? null : context.getAuth().projectId();
    }

    /**
     * 调用日志租户：虚拟 Key 用绑定租户；master/未绑定记默认租户。
     *
     * @param context 治理上下文
     * @return 租户 ID
     */
    private String tenantOf(GatewayContext context) {
        if (!ObjectUtils.isEmpty(context.getAuth())
                && StringUtils.hasText(context.getAuth().tenantId())) {
            return context.getAuth().tenantId();
        }
        return TenantScopeGuard.DEFAULT_TENANT;
    }

    /**
     * 解析请求体；失败返回 null，由业务层产生标准错误。
     *
     * @param requestBody 请求体
     * @return JSON 或 null
     */
    private JsonNode parseQuietly(String requestBody) {
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            return ObjectUtils.isEmpty(root) || !root.isObject() ? null : root;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * 请求耗时（从上下文创建到记录日志）。
     *
     * @param context 治理上下文
     * @return 毫秒
     */
    private long latencyMs(GatewayContext context) {
        return Math.max(0L, System.currentTimeMillis() - context.getStartedAt());
    }
}
