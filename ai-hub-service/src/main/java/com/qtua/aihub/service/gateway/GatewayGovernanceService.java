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

    private final ModelCatalog modelCatalog;
    private final GatewayGuard gatewayGuard;
    private final PricingCatalog pricingCatalog;
    private final CallLogManager callLogManager;
    private final OpenAiProtocolAdapter protocolAdapter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造治理服务。
     *
     * @param modelCatalog        模型目录
     * @param gatewayGuard        限流与预算端口
     * @param pricingCatalog      价格目录
     * @param callLogManager      调用日志与消费累计门面
     * @param protocolAdapter     OpenAI 协议适配器（Token 估算）
     * @param rateLimitProperties 限流配置
     * @param objectMapper        JSON 解析器
     */
    public GatewayGovernanceService(ModelCatalog modelCatalog,
                                    GatewayGuard gatewayGuard,
                                    PricingCatalog pricingCatalog,
                                    CallLogManager callLogManager,
                                    OpenAiProtocolAdapter protocolAdapter,
                                    RateLimitProperties rateLimitProperties,
                                    ObjectMapper objectMapper) {
        this.modelCatalog = modelCatalog;
        this.gatewayGuard = gatewayGuard;
        this.pricingCatalog = pricingCatalog;
        this.callLogManager = callLogManager;
        this.protocolAdapter = protocolAdapter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
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
     * 成功路径：按实际用量计价、结算、累计 spend、写日志。
     *
     * @param context 治理上下文
     */
    public void complete(GatewayContext context) {
        if (ObjectUtils.isEmpty(context) || !context.hasModelAlias()) {
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
        if (context.hasModelAlias()) {
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
                    latencyMs(context), status, errorType, null, Instant.now(),
                    context.getServedAlias(), context.getRetryCount(),
                    context.isFallbackUsed()));
        } catch (RuntimeException ex) {
            // 日志失败不影响业务响应。
        }
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
