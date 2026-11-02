package com.qtua.aihub.core;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.RateLimitProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenEstimate;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.integration.ratelimit.RedisRateLimiter;
import com.qtua.aihub.manager.CallLogManager;
import com.qtua.aihub.manager.model.CallLogBO;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 网关核心编排。
 *
 * <p>职责：授权 → 限流/预算检查 → 选择部署 → 协议适配 → 调用上游 → 计量结算与日志。
 * 只消费 BO，不直接处理 HTTP/DTO；协议字段改写由 integration 层完成。
 * 本类不保存请求级状态，可被多实例安全使用。</p>
 */
@Component
public class ChatCompletionCore {

    private static final Logger LOG = LoggerFactory.getLogger(ChatCompletionCore.class);

    private final DeploymentSelector deploymentSelector;
    private final UpstreamClient upstreamClient;
    private final OpenAiProtocolAdapter protocolAdapter;
    private final PricingCatalog pricingCatalog;
    private final CallLogManager callLogManager;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    /**
     * 构造核心编排器。
     *
     * @param deploymentSelector  部署选择策略
     * @param upstreamClient      上游 HTTP 客户端
     * @param protocolAdapter     OpenAI 协议适配器
     * @param pricingCatalog      模型价格目录
     * @param callLogManager      调用日志与消费累计门面
     * @param rateLimiter         Redis 限流与预算计数器
     * @param rateLimitProperties 限流配置
     */
    public ChatCompletionCore(DeploymentSelector deploymentSelector,
                              UpstreamClient upstreamClient,
                              OpenAiProtocolAdapter protocolAdapter,
                              PricingCatalog pricingCatalog,
                              CallLogManager callLogManager,
                              RedisRateLimiter rateLimiter,
                              RateLimitProperties rateLimitProperties) {
        this.deploymentSelector = deploymentSelector;
        this.upstreamClient = upstreamClient;
        this.protocolAdapter = protocolAdapter;
        this.pricingCatalog = pricingCatalog;
        this.callLogManager = callLogManager;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * 执行一次聊天补全转发。
     *
     * <p>成功与失败都会记录调用日志；成功时按价格表计算成本并累加虚拟 Key 的 spend。
     * 限流/预算在调用前检查并预占，响应后按实际用量结算；计量异常不影响业务响应。</p>
     *
     * @param bo 聊天补全业务对象
     * @return 上游响应业务对象（响应 model 已回写为别名）
     */
    public ChatCompletionResultBO chat(ChatCompletionBO bo) {
        long startNanos = System.nanoTime();
        String deploymentModel = null;
        TokenEstimate estimate = TokenEstimate.of(0, 0);
        BigDecimal estimatedCost = BigDecimal.ZERO;
        boolean tpmReserved = false;
        boolean budgetReserved = false;
        try {
            // 先做授权再判断模型是否存在：虚拟 Key 越权请求统一返回 403，避免泄露模型目录。
            assertModelAccess(bo);
            if (ObjectUtils.isEmpty(bo.getDeployments())) {
                throw ChatCompletionException.invalidModel(bo.getAlias());
            }
            assertRpm(bo);
            estimate = protocolAdapter.estimateTokens(
                    bo.getRequestBody(), rateLimitProperties.getDefaultMaxTokens());
            ModelDeployment deployment = deploymentSelector.select(bo.getAlias(), bo.getDeployments());
            deploymentModel = deployment.upstreamModel();
            LOG.debug("routed alias={} upstreamModel={}", bo.getAlias(), deploymentModel);
            tpmReserved = assertTpm(bo, estimate);
            estimatedCost = calculateCost(deploymentModel, new TokenUsage(
                    estimate.inputTokens(), estimate.maxOutputTokens(), estimate.totalTokens()));
            budgetReserved = reserveBudget(bo, estimatedCost);
            String upstreamBody = protocolAdapter.rewriteRequestModel(
                    bo.getRequestBody(), deployment.upstreamModel());
            String upstreamResponse = upstreamClient.chat(deployment, upstreamBody);
            TokenUsage usage = protocolAdapter.extractUsage(upstreamResponse);
            BigDecimal cost = calculateCost(deploymentModel, usage);
            String responseBody = protocolAdapter.rewriteResponseModel(upstreamResponse, bo.getAlias());
            settleLimits(bo, deploymentModel, estimate, usage, estimatedCost, cost,
                    tpmReserved, budgetReserved);
            recordLog(bo, deploymentModel, usage, cost, HttpStatus.OK.value(), null, latencyMs(startNanos));
            callLogManager.addSpend(bo.getKeyHash(), cost);
            return new ChatCompletionResultBO(responseBody);
        } catch (ChatCompletionException ex) {
            // 业务失败（越权/未知模型/限流/预算/上游失败）同样留痕，便于审计与排障。
            releaseLimits(bo, deploymentModel, estimate, estimatedCost, tpmReserved, budgetReserved);
            recordLog(bo, deploymentModel, TokenUsage.ZERO, BigDecimal.ZERO,
                    ex.getStatus().value(), ex.getType(), latencyMs(startNanos));
            throw ex;
        } catch (RuntimeException ex) {
            releaseLimits(bo, deploymentModel, estimate, estimatedCost, tpmReserved, budgetReserved);
            recordLog(bo, deploymentModel, TokenUsage.ZERO, BigDecimal.ZERO,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), "internal_error", latencyMs(startNanos));
            throw ex;
        }
    }

    /**
     * 检查 RPM 限流。
     *
     * @param bo 聊天补全业务对象
     */
    private void assertRpm(ChatCompletionBO bo) {
        if (!StringUtils.hasText(bo.getKeyHash())) {
            return;
        }
        if (!rateLimiter.tryAcquireRpm(bo.getKeyHash(), bo.getAlias(),
                bo.getRequestId(), bo.getRpmLimit())) {
            throw ChatCompletionException.rateLimitExceeded("requests per minute limit");
        }
    }

    /**
     * 预占 TPM 额度。
     *
     * @param bo       聊天补全业务对象
     * @param estimate Token 估算
     * @return 是否成功预占（未配置限制时返回 false）
     */
    private boolean assertTpm(ChatCompletionBO bo, TokenEstimate estimate) {
        if (!StringUtils.hasText(bo.getKeyHash()) || ObjectUtils.isEmpty(bo.getTpmLimit())) {
            return false;
        }
        if (!rateLimiter.tryAcquireTpm(bo.getKeyHash(), bo.getAlias(), bo.getRequestId(),
                estimate.totalTokens(), bo.getTpmLimit())) {
            throw ChatCompletionException.rateLimitExceeded("tokens per minute limit");
        }
        return true;
    }

    /**
     * 预占预算额度。
     *
     * @param bo            聊天补全业务对象
     * @param estimatedCost 估算成本
     * @return 是否成功预占（未配置预算时返回 false）
     */
    private boolean reserveBudget(ChatCompletionBO bo, BigDecimal estimatedCost) {
        if (!StringUtils.hasText(bo.getKeyHash()) || ObjectUtils.isEmpty(bo.getMaxBudget())
                || !StringUtils.hasText(bo.getBudgetDuration())) {
            return false;
        }
        if (!rateLimiter.tryReserveBudget(bo.getKeyHash(), bo.getBudgetDuration(),
                bo.getMaxBudget(), estimatedCost)) {
            throw ChatCompletionException.budgetExceeded();
        }
        return true;
    }

    /**
     * 成功路径：按实际用量结算 TPM 与预算。
     *
     * @param bo              请求业务对象
     * @param deploymentModel 上游真实模型名
     * @param estimate        Token 估算
     * @param usage           实际用量
     * @param estimatedCost   预占成本
     * @param actualCost      实际成本
     * @param tpmReserved     TPM 是否已预占
     * @param budgetReserved  预算是否已预占
     */
    private void settleLimits(ChatCompletionBO bo, String deploymentModel, TokenEstimate estimate,
                              TokenUsage usage, BigDecimal estimatedCost, BigDecimal actualCost,
                              boolean tpmReserved, boolean budgetReserved) {
        if (tpmReserved) {
            rateLimiter.settleTpm(bo.getKeyHash(), bo.getAlias(), bo.getRequestId(),
                    estimate.totalTokens(), usage.totalTokens());
        }
        if (budgetReserved) {
            rateLimiter.settleBudget(bo.getKeyHash(), bo.getBudgetDuration(),
                    estimatedCost, actualCost);
        }
    }

    /**
     * 失败路径：释放 TPM 与预算预占。
     *
     * @param bo             请求业务对象
     * @param deploymentModel 上游真实模型名
     * @param estimate       Token 估算
     * @param estimatedCost  预占成本
     * @param tpmReserved    TPM 是否已预占
     * @param budgetReserved 预算是否已预占
     */
    private void releaseLimits(ChatCompletionBO bo, String deploymentModel, TokenEstimate estimate,
                               BigDecimal estimatedCost, boolean tpmReserved, boolean budgetReserved) {
        if (tpmReserved) {
            rateLimiter.settleTpm(bo.getKeyHash(), bo.getAlias(), bo.getRequestId(),
                    estimate.totalTokens(), 0);
        }
        if (budgetReserved) {
            rateLimiter.releaseBudget(bo.getKeyHash(), bo.getBudgetDuration(), estimatedCost);
        }
    }

    /**
     * 按上游模型与用量计算成本；未配置价格时按 0 处理。
     *
     * @param upstreamModel 上游真实模型名
     * @param usage         Token 用量
     * @return 成本
     */
    private BigDecimal calculateCost(String upstreamModel, TokenUsage usage) {
        return pricingCatalog.findPrice(upstreamModel)
                .map(price -> price.costOf(usage))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 记录调用日志；任何计量异常都不能影响业务响应。
     *
     * @param bo              请求业务对象
     * @param deploymentModel 上游真实模型名
     * @param usage           Token 用量
     * @param cost            成本
     * @param status          HTTP 状态码
     * @param errorType       错误/限流类型；成功为 null
     * @param latencyMs       耗时（毫秒）
     */
    private void recordLog(ChatCompletionBO bo, String deploymentModel, TokenUsage usage,
                           BigDecimal cost, int status, String errorType, long latencyMs) {
        try {
            callLogManager.record(new CallLogBO(bo.getRequestId(), bo.getKeyHash(), bo.getAlias(),
                    deploymentModel, usage, cost, latencyMs, status, errorType, null, Instant.now()));
        } catch (RuntimeException ex) {
            LOG.error("failed to record call log requestId={} alias={}", bo.getRequestId(),
                    bo.getAlias(), ex);
        }
    }

    /**
     * 计算从入口到当前的毫秒耗时。
     *
     * @param startNanos 起始纳秒时间
     * @return 耗时（毫秒）
     */
    private long latencyMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * 校验调用方是否有权访问请求的模型别名。
     *
     * <p>master key 全量放行；虚拟 Key 的白名单为空表示不限模型。</p>
     *
     * @param bo 聊天补全业务对象
     */
    private void assertModelAccess(ChatCompletionBO bo) {
        if (bo.isMasterKey() || bo.getAllowedModels().isEmpty()
                || bo.getAllowedModels().contains(bo.getAlias())) {
            return;
        }
        throw ChatCompletionException.keyModelAccessDenied(bo.getAlias());
    }
}
