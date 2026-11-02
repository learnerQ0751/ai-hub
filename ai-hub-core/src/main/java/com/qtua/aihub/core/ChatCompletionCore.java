package com.qtua.aihub.core;

import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.GatewayProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.common.resilience.DeploymentHealthStore;
import com.qtua.aihub.core.routing.DeploymentSelector;
import com.qtua.aihub.integration.UpstreamClient;
import com.qtua.aihub.integration.protocol.OpenAiProtocolAdapter;
import com.qtua.aihub.manager.model.ChatCompletionBO;
import com.qtua.aihub.manager.model.ChatCompletionResultBO;
import com.qtua.aihub.manager.model.ChatCompletionStreamResultBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天补全业务编排。
 *
 * <p>负责：容错编排（重试 / 主备 / fallback）、部署选择、协议改写、上游调用、usage 解析。
 * 限流、预算、计价、spend 与访问日志属于网关治理层（service/gateway），本类不感知。
 * 本类不保存请求级状态（失败集合为方法局部变量），可被多实例安全使用。</p>
 *
 * <p>容错顺序：同一别名内按 priority 主备 + num-retries 重试 → 别名级 fallbacks 降级链。
 * 请求内失败过的部署在存在其他候选时不再选择；跨请求的冷却状态由
 * {@link DeploymentHealthStore}（Redis）维护。</p>
 */
@Component
public class ChatCompletionCore {

    private static final Logger LOG = LoggerFactory.getLogger(ChatCompletionCore.class);
    private static final int MIN_RETRIES = 0;
    private static final int DEFAULT_PRIORITY = 0;

    private final DeploymentSelector deploymentSelector;
    private final UpstreamClient upstreamClient;
    private final OpenAiProtocolAdapter protocolAdapter;
    private final ModelCatalog modelCatalog;
    private final DeploymentHealthStore healthStore;
    private final GatewayProperties properties;

    /**
     * 构造核心编排器。
     *
     * @param deploymentSelector 部署选择策略
     * @param upstreamClient     上游 HTTP 客户端
     * @param protocolAdapter    OpenAI 协议适配器
     * @param modelCatalog       模型目录（fallback 别名查询）
     * @param healthStore        部署健康状态（冷却）
     * @param properties         网关配置（容错参数）
     */
    public ChatCompletionCore(DeploymentSelector deploymentSelector,
                              UpstreamClient upstreamClient,
                              OpenAiProtocolAdapter protocolAdapter,
                              ModelCatalog modelCatalog,
                              DeploymentHealthStore healthStore,
                              GatewayProperties properties) {
        this.deploymentSelector = deploymentSelector;
        this.upstreamClient = upstreamClient;
        this.protocolAdapter = protocolAdapter;
        this.modelCatalog = modelCatalog;
        this.healthStore = healthStore;
        this.properties = properties;
    }

    /**
     * 执行一次聊天补全转发（带容错）。
     *
     * @param bo 聊天补全业务对象
     * @return 上游响应、用量、实际部署模型与容错轨迹
     */
    public ChatCompletionResultBO chat(ChatCompletionBO bo) {
        if (ObjectUtils.isEmpty(bo.getDeployments())) {
            throw ChatCompletionException.invalidModel(bo.getAlias());
        }
        List<String> aliasChain = aliasChain(bo.getAlias());
        Set<ModelDeployment> failedDeployments = new HashSet<>();
        ChatCompletionException lastError = null;
        int retryCount = 0;
        for (int aliasIndex = 0; aliasIndex < aliasChain.size(); aliasIndex++) {
            String servedAlias = aliasChain.get(aliasIndex);
            List<ModelDeployment> deployments = deploymentsOf(bo, servedAlias, aliasIndex);
            if (ObjectUtils.isEmpty(deployments)) {
                continue;
            }
            int attempts = 0;
            while (attempts <= maxRetries()) {
                ModelDeployment deployment = choose(deployments, failedDeployments);
                if (ObjectUtils.isEmpty(deployment)) {
                    break;
                }
                try {
                    ChatCompletionResultBO result = invoke(bo, servedAlias, deployment,
                            retryCount, aliasIndex > 0);
                    healthStore.recordSuccess(deployment);
                    return result;
                } catch (ChatCompletionException ex) {
                    // 4xx 等请求自身错误不进入容错，直接返回。
                    if (!ex.isRetryable()) {
                        throw ex;
                    }
                    healthStore.recordFailure(deployment);
                    failedDeployments.add(deployment);
                    lastError = ex;
                    retryCount++;
                    attempts++;
                    LOG.warn("upstream attempt failed alias={} upstreamModel={} attempt={} type={}",
                            servedAlias, deployment.upstreamModel(), attempts, ex.getType());
                }
            }
        }
        if (Objects.nonNull(lastError)) {
            throw lastError;
        }
        throw ChatCompletionException.invalidModel(bo.getAlias());
    }

    /**
     * 执行一次流式聊天补全转发。
     *
     * <p>流式选择部署时复用冷却过滤与 priority 主备，但不在流内重试或切换：
     * 一旦开始向下游发送 SSE，错误只能以流事件形式传达。流结束后记录部署健康状态。</p>
     *
     * @param bo 聊天补全业务对象
     * @return SSE data 流、usage 引用与部署信息
     */
    public ChatCompletionStreamResultBO chatStream(ChatCompletionBO bo) {
        if (ObjectUtils.isEmpty(bo.getDeployments())) {
            throw ChatCompletionException.invalidModel(bo.getAlias());
        }
        ModelDeployment deployment = choose(bo.getDeployments(), Set.of());
        if (Objects.isNull(deployment)) {
            throw ChatCompletionException.invalidModel(bo.getAlias());
        }
        LOG.debug("stream routed alias={} upstreamModel={}",
                bo.getAlias(), deployment.upstreamModel());
        String upstreamBody = protocolAdapter.rewriteStreamRequest(
                bo.getRequestBody(), deployment.upstreamModel());
        AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.ZERO);
        Flux<String> events = upstreamClient.stream(deployment, upstreamBody)
                .map(data -> {
                    TokenUsage parsed = protocolAdapter.extractUsage(data);
                    if (Objects.nonNull(parsed) && parsed.totalTokens() > 0) {
                        usage.set(parsed);
                    }
                    return protocolAdapter.rewriteStreamResponseModel(data, bo.getAlias());
                })
                .doOnError(ChatCompletionException.class, ex -> {
                    if (ex.isRetryable()) {
                        healthStore.recordFailure(deployment);
                    }
                })
                .doOnComplete(() -> healthStore.recordSuccess(deployment));
        return new ChatCompletionStreamResultBO(events, usage, bo.getAlias(),
                deployment.upstreamModel());
    }

    /**
     * 构建别名链：请求别名 + fallback 别名，去重保序。
     *
     * @param alias 请求别名
     * @return 别名链
     */
    private List<String> aliasChain(String alias) {
        Set<String> chain = new LinkedHashSet<>();
        chain.add(alias);
        chain.addAll(modelCatalog.findFallbacks(alias));
        return new ArrayList<>(chain);
    }

    /**
     * 取当前别名的部署列表；主别名复用已解析结果，fallback 别名查目录。
     *
     * @param bo        业务对象
     * @param alias     当前别名
     * @param aliasIndex 别名链下标
     * @return 部署列表
     */
    private List<ModelDeployment> deploymentsOf(ChatCompletionBO bo, String alias, int aliasIndex) {
        if (aliasIndex == 0) {
            return bo.getDeployments();
        }
        return modelCatalog.findDeployments(alias);
    }

    /**
     * 选择部署：冷却过滤 → 请求内失败过滤 → priority 分层 → 加权随机。
     *
     * <p>全部部署冷却时忽略冷却（fail-open）；候选只剩失败过的部署时允许重试，
     * 保证单部署场景下的 num-retries 生效。</p>
     *
     * @param deployments        别名下全部部署
     * @param failedDeployments  本请求内已失败的部署
     * @return 选中的部署；无候选时为 null
     */
    private ModelDeployment choose(List<ModelDeployment> deployments,
                                   Set<ModelDeployment> failedDeployments) {
        List<ModelDeployment> healthy = deployments.stream()
                .filter(deployment -> !healthStore.isCoolingDown(deployment))
                .toList();
        List<ModelDeployment> preferred = healthy.stream()
                .filter(deployment -> !failedDeployments.contains(deployment))
                .toList();
        List<ModelDeployment> candidates;
        if (!preferred.isEmpty()) {
            candidates = preferred;
        } else if (!healthy.isEmpty()) {
            // 所有健康部署都在本请求内失败过：允许重试（单部署场景）。
            candidates = healthy;
        } else {
            // 全部冷却：可用性优先，忽略冷却。
            LOG.warn("all deployments are cooling down alias={}, falling back to full candidates",
                    deployments.get(0).alias());
            candidates = deployments;
        }
        return selectByPriority(candidates);
    }

    /**
     * 按 priority 取最高优先级层（数值最小），同层内交给选择器加权随机。
     *
     * @param candidates 候选部署
     * @return 选中部署
     */
    private ModelDeployment selectByPriority(List<ModelDeployment> candidates) {
        int minPriority = candidates.stream()
                .mapToInt(ModelDeployment::priority)
                .min()
                .orElse(DEFAULT_PRIORITY);
        List<ModelDeployment> layer = candidates.stream()
                .filter(deployment -> deployment.priority() == minPriority)
                .toList();
        return deploymentSelector.select(layer.get(0).alias(), layer);
    }

    /**
     * 执行单次上游调用并组装结果。
     *
     * @param bo           业务对象
     * @param servedAlias  当前服务的别名
     * @param deployment   选中的部署
     * @param retryCount   已发生的失败尝试次数
     * @param fallbackUsed 是否已进入 fallback 别名
     * @return 结果
     */
    private ChatCompletionResultBO invoke(ChatCompletionBO bo, String servedAlias,
                                          ModelDeployment deployment, int retryCount,
                                          boolean fallbackUsed) {
        LOG.debug("routed alias={} servedAlias={} upstreamModel={}",
                bo.getAlias(), servedAlias, deployment.upstreamModel());
        String upstreamBody = protocolAdapter.rewriteRequestModel(
                bo.getRequestBody(), deployment.upstreamModel());
        String upstreamResponse = upstreamClient.chat(deployment, upstreamBody);
        TokenUsage usage = protocolAdapter.extractUsage(upstreamResponse);
        // 对外始终回写请求别名，fallback 不改变客户端看到的模型名。
        String responseBody = protocolAdapter.rewriteResponseModel(upstreamResponse, bo.getAlias());
        return new ChatCompletionResultBO(responseBody, usage, deployment.upstreamModel(),
                servedAlias, retryCount, fallbackUsed);
    }

    private int maxRetries() {
        return Math.max(MIN_RETRIES, properties.getResilience().getNumRetries());
    }
}
