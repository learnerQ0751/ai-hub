package com.qtua.aihub.service.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
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
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
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
import java.util.concurrent.TimeUnit;

/**
 * 网关聊天补全服务。
 *
 * <p>承担网关治理职责：请求解析/校验、模型白名单、限流、预算、计价、spend 累计与访问日志；
 * 业务执行通过 {@link ChatExecutor} 端口，core 只负责上游转发与 usage 解析。
 * 未来拆独立网关应用时，本类与 start 层可整体迁移，业务侧只需提供 HTTP 执行器。</p>
 */
@Service
public class GatewayChatCompletionService implements ChatCompletionService {

    private final ModelCatalog modelCatalog;
    private final ChatExecutor chatExecutor;
    private final GatewayGuard gatewayGuard;
    private final PricingCatalog pricingCatalog;
    private final CallLogManager callLogManager;
    private final OpenAiProtocolAdapter protocolAdapter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造网关服务。
     *
     * @param modelCatalog        模型目录
     * @param chatExecutor        业务执行出口
     * @param gatewayGuard        限流与预算端口
     * @param pricingCatalog      价格目录
     * @param callLogManager      调用日志与消费累计门面
     * @param protocolAdapter     OpenAI 协议适配器（Token 估算）
     * @param rateLimitProperties 限流配置
     * @param objectMapper        JSON 解析器
     */
    public GatewayChatCompletionService(ModelCatalog modelCatalog,
                                        ChatExecutor chatExecutor,
                                        GatewayGuard gatewayGuard,
                                        PricingCatalog pricingCatalog,
                                        CallLogManager callLogManager,
                                        OpenAiProtocolAdapter protocolAdapter,
                                        RateLimitProperties rateLimitProperties,
                                        ObjectMapper objectMapper) {
        this.modelCatalog = modelCatalog;
        this.chatExecutor = chatExecutor;
        this.gatewayGuard = gatewayGuard;
        this.pricingCatalog = pricingCatalog;
        this.callLogManager = callLogManager;
        this.protocolAdapter = protocolAdapter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一次聊天补全请求。
     *
     * @param reqDTO 原始请求
     * @return 原始响应
     */
    @Override
    public ChatCompletionResDTO chat(ChatCompletionReqDTO reqDTO) {
        long startNanos = System.nanoTime();
        String requestBody = reqDTO.getRawBody();
        JsonNode root = parse(requestBody);
        String alias = requireModel(root);
        rejectStreaming(root);
        requireMessages(root);
        KeyAuthDTO auth = reqDTO.getAuth();
        GuardReservation reservation = null;
        try {
            // 授权与模型目录也属于网关治理；失败需要留痕（403/400）。
            assertModelAccess(auth, alias);
            List<ModelDeployment> deployments = modelCatalog.findDeployments(alias);
            if (ObjectUtils.isEmpty(deployments)) {
                throw ChatCompletionException.invalidModel(alias);
            }
            ChatCompletionBO bo = new ChatCompletionBO(alias, deployments, requestBody,
                    reqDTO.getRequestId(), auth.keyHash());
            TokenEstimate estimate = protocolAdapter.estimateTokens(
                    requestBody, rateLimitProperties.getDefaultMaxTokens());
            GuardRequest guardRequest = new GuardRequest(reqDTO.getRequestId(), auth.keyHash(), alias,
                    auth.rpmLimit(), auth.tpmLimit(), auth.maxBudget(), auth.budgetDuration(),
                    estimate.totalTokens(), estimateCost(deployments, estimate));
            reservation = gatewayGuard.reserve(guardRequest);
            ChatCompletionResultBO result = chatExecutor.execute(bo);
            BigDecimal cost = calculateCost(result);
            gatewayGuard.settle(reservation, result.getUsage(), cost);
            callLogManager.addSpend(auth.keyHash(), cost);
            recordLog(reqDTO, alias, result.getDeploymentModel(), result.getUsage(), cost,
                    HttpStatus.OK.value(), null, startNanos);
            return new ChatCompletionResDTO(result.getResponseBody());
        } catch (ChatCompletionException ex) {
            releaseIfNeeded(reservation);
            recordLog(reqDTO, alias, null, TokenUsage.ZERO, BigDecimal.ZERO,
                    ex.getStatus().value(), ex.getType(), startNanos);
            throw ex;
        } catch (RuntimeException ex) {
            releaseIfNeeded(reservation);
            recordLog(reqDTO, alias, null, TokenUsage.ZERO, BigDecimal.ZERO,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), "internal_error", startNanos);
            throw ex;
        }
    }

    /**
     * 校验虚拟 Key 的模型白名单。
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
     * @param result 业务结果
     * @return 实际成本
     */
    private BigDecimal calculateCost(ChatCompletionResultBO result) {
        return pricingCatalog.findPrice(result.getDeploymentModel())
                .map(price -> price.costOf(result.getUsage()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 记录调用日志；任何计量异常都不能影响业务响应。
     *
     * @param reqDTO          请求
     * @param alias           模型别名
     * @param deploymentModel 上游模型
     * @param usage           Token 用量
     * @param cost            成本
     * @param status          HTTP 状态码
     * @param errorType       错误类型；成功为 null
     * @param startNanos      起始纳秒
     */
    private void recordLog(ChatCompletionReqDTO reqDTO, String alias, String deploymentModel,
                           TokenUsage usage, BigDecimal cost, int status, String errorType,
                           long startNanos) {
        try {
            callLogManager.record(new CallLogBO(reqDTO.getRequestId(),
                    reqDTO.getAuth().keyHash(), alias, deploymentModel, usage, cost,
                    latencyMs(startNanos), status, errorType, null, Instant.now()));
        } catch (RuntimeException ex) {
            // 日志失败不影响业务响应。
        }
    }

    /**
     * 释放已存在的预占。
     *
     * @param reservation 预占结果；可为 null
     */
    private void releaseIfNeeded(GuardReservation reservation) {
        if (Objects.nonNull(reservation)) {
            gatewayGuard.release(reservation);
        }
    }

    /**
     * 计算从入口到当前的毫秒耗时。
     *
     * @param startNanos 起始纳秒
     * @return 耗时（毫秒）
     */
    private long latencyMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * 解析 JSON 并确保是对象。
     *
     * @param requestBody 原始请求体
     * @return 解析后的 JSON
     */
    private JsonNode parse(String requestBody) {
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            if (ObjectUtils.isEmpty(root) || !root.isObject()) {
                throw ChatCompletionException.invalidRequest("Request body must be a JSON object", null);
            }
            return root;
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.invalidRequest("Invalid JSON request body", null);
        }
    }

    /**
     * 校验并读取 model 字段。
     *
     * @param root 请求 JSON
     * @return 模型别名
     */
    private String requireModel(JsonNode root) {
        JsonNode modelNode = root.get(JsonFields.MODEL);
        if (ObjectUtils.isEmpty(modelNode) || modelNode.isNull()
                || !StringUtils.hasText(modelNode.asText())) {
            throw ChatCompletionException.invalidRequest("Field 'model' is required", JsonFields.MODEL);
        }
        return modelNode.asText();
    }

    /**
     * 本期明确拒绝流式请求，避免静默返回非流式结果。
     *
     * @param root 请求 JSON
     */
    private void rejectStreaming(JsonNode root) {
        JsonNode streamNode = root.get(JsonFields.STREAM);
        if (!ObjectUtils.isEmpty(streamNode) && streamNode.asBoolean(false)) {
            throw ChatCompletionException.invalidRequest("Streaming is not supported yet", JsonFields.STREAM);
        }
    }

    /**
     * 校验 messages 为非空数组。
     *
     * @param root 请求 JSON
     */
    private void requireMessages(JsonNode root) {
        JsonNode messages = root.get(JsonFields.MESSAGES);
        if (ObjectUtils.isEmpty(messages) || !messages.isArray() || messages.isEmpty()) {
            throw ChatCompletionException.invalidRequest(
                    "Field 'messages' must be a non-empty array", JsonFields.MESSAGES);
        }
    }
}
