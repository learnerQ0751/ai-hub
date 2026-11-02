package com.qtua.aihub.core;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.pricing.PricingCatalog;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.CallLogManager;
import com.qtua.aihub.manager.model.CallLogBO;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 网关核心编排。
 *
 * <p>职责：选择部署 → 协议适配 → 调用上游 → Token 计量与日志。
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

    /**
     * 构造核心编排器。
     *
     * @param deploymentSelector 部署选择策略
     * @param upstreamClient     上游 HTTP 客户端
     * @param protocolAdapter    OpenAI 协议适配器
     * @param pricingCatalog     模型价格目录
     * @param callLogManager     调用日志与消费累计门面
     */
    public ChatCompletionCore(DeploymentSelector deploymentSelector,
                              UpstreamClient upstreamClient,
                              OpenAiProtocolAdapter protocolAdapter,
                              PricingCatalog pricingCatalog,
                              CallLogManager callLogManager) {
        this.deploymentSelector = deploymentSelector;
        this.upstreamClient = upstreamClient;
        this.protocolAdapter = protocolAdapter;
        this.pricingCatalog = pricingCatalog;
        this.callLogManager = callLogManager;
    }

    /**
     * 执行一次聊天补全转发。
     *
     * <p>成功与失败都会记录调用日志；成功时按价格表计算成本并累加虚拟 Key 的 spend。
     * 计量异常不影响业务响应。</p>
     *
     * @param bo 聊天补全业务对象
     * @return 上游响应业务对象（响应 model 已回写为别名）
     */
    public ChatCompletionResultBO chat(ChatCompletionBO bo) {
        long startNanos = System.nanoTime();
        String deploymentModel = null;
        try {
            // 先做授权再判断模型是否存在：虚拟 Key 越权请求统一返回 403，避免泄露模型目录。
            assertModelAccess(bo);
            if (ObjectUtils.isEmpty(bo.getDeployments())) {
                throw ChatCompletionException.invalidModel(bo.getAlias());
            }
            ModelDeployment deployment = deploymentSelector.select(bo.getAlias(), bo.getDeployments());
            deploymentModel = deployment.upstreamModel();
            LOG.debug("routed alias={} upstreamModel={}", bo.getAlias(), deploymentModel);
            String upstreamBody = protocolAdapter.rewriteRequestModel(
                    bo.getRequestBody(), deployment.upstreamModel());
            String upstreamResponse = upstreamClient.chat(deployment, upstreamBody);
            TokenUsage usage = protocolAdapter.extractUsage(upstreamResponse);
            BigDecimal cost = calculateCost(deploymentModel, usage);
            String responseBody = protocolAdapter.rewriteResponseModel(upstreamResponse, bo.getAlias());
            recordLog(bo, deploymentModel, usage, cost, HttpStatus.OK.value(), latencyMs(startNanos));
            callLogManager.addSpend(bo.getKeyHash(), cost);
            return new ChatCompletionResultBO(responseBody);
        } catch (ChatCompletionException ex) {
            // 业务失败（越权/未知模型/上游失败）同样留痕，便于审计与排障。
            recordLog(bo, deploymentModel, TokenUsage.ZERO, BigDecimal.ZERO,
                    ex.getStatus().value(), latencyMs(startNanos));
            throw ex;
        } catch (RuntimeException ex) {
            recordLog(bo, deploymentModel, TokenUsage.ZERO, BigDecimal.ZERO,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), latencyMs(startNanos));
            throw ex;
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
     * @param latencyMs       耗时（毫秒）
     */
    private void recordLog(ChatCompletionBO bo, String deploymentModel, TokenUsage usage,
                           BigDecimal cost, int status, long latencyMs) {
        try {
            callLogManager.record(new CallLogBO(bo.getRequestId(), bo.getKeyHash(), bo.getAlias(),
                    deploymentModel, usage, cost, latencyMs, status, null, Instant.now()));
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
