package com.qtua.aihub.manager;

import com.qtua.aihub.dal.mapper.ApiKeyMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import com.qtua.aihub.manager.model.CallLogBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 调用日志与消费累计门面。
 *
 * <p>日志通过有界线程池异步写入，不阻塞响应；执行器饱和时由调用线程兜底执行，
 * 写入失败以 ERROR 日志留下可恢复的结构化字段。spend 用原子 SQL 同步累加，
 * 保证预算判断（REQ-1.4）尽快看到最新消费。本类无可变业务状态，多实例安全。</p>
 */
@Component
public class CallLogManager {

    private static final Logger LOG = LoggerFactory.getLogger(CallLogManager.class);

    private final CallLogMapper callLogMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final Executor executor;

    /**
     * 构造门面。
     *
     * @param callLogMapper 调用日志 Mapper
     * @param apiKeyMapper  Key Mapper（spend 累加）
     * @param executor      日志写入执行器
     */
    public CallLogManager(CallLogMapper callLogMapper,
                          ApiKeyMapper apiKeyMapper,
                          @Qualifier("callLogExecutor") Executor executor) {
        this.callLogMapper = callLogMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.executor = executor;
    }

    /**
     * 异步记录一条调用日志。
     *
     * <p>任何异常都被吞掉：计量失败不能影响已完成的业务调用。</p>
     *
     * @param callLog 调用日志
     */
    public void record(CallLogBO callLog) {
        try {
            executor.execute(() -> insert(callLog));
        } catch (RejectedExecutionException ex) {
            // 执行器饱和：退回调用线程，宁可多花一点时间也不能丢审计数据。
            insert(callLog);
        }
    }

    /**
     * 原子累加虚拟 Key 的消费金额。
     *
     * <p>master 调用没有 keyId，直接跳过；金额为 0 时不写库。</p>
     *
     * @param keyId 虚拟 Key 的 SHA-256
     * @param cost  本次成本
     */
    public void addSpend(String keyId, BigDecimal cost) {
        if (!StringUtils.hasText(keyId) || ObjectUtils.isEmpty(cost) || cost.signum() <= 0) {
            return;
        }
        try {
            apiKeyMapper.addSpend(keyId, cost);
        } catch (RuntimeException ex) {
            // 已完成的调用不能因为累计失败而失败；记录金额供人工补账。
            LOG.error("failed to accumulate spend keyId={} cost={}", keyId, cost, ex);
        }
    }

    /**
     * 实际写库；失败时输出可恢复的完整字段。
     *
     * @param callLog 调用日志
     */
    private void insert(CallLogBO callLog) {
        try {
            callLogMapper.insert(toDO(callLog));
        } catch (RuntimeException ex) {
            LOG.error("failed to persist call log requestId={} keyId={} alias={} deployment={} "
                            + "promptTokens={} completionTokens={} totalTokens={} cost={} "
                            + "latencyMs={} status={}",
                    callLog.requestId(), callLog.keyId(), callLog.modelAlias(),
                    callLog.deploymentModel(), callLog.usage().promptTokens(),
                    callLog.usage().completionTokens(), callLog.usage().totalTokens(),
                    callLog.cost(), callLog.latencyMs(), callLog.status(), ex);
        }
    }

    /**
     * BO → DO。
     *
     * @param callLog 业务对象
     * @return 数据对象
     */
    private CallLogDO toDO(CallLogBO callLog) {
        CallLogDO dataObject = new CallLogDO();
        dataObject.setRequestId(callLog.requestId());
        dataObject.setKeyId(callLog.keyId());
        dataObject.setModelAlias(callLog.modelAlias());
        dataObject.setDeploymentModel(callLog.deploymentModel());
        dataObject.setPromptTokens(callLog.usage().promptTokens());
        dataObject.setCompletionTokens(callLog.usage().completionTokens());
        dataObject.setTotalTokens(callLog.usage().totalTokens());
        dataObject.setCost(callLog.cost());
        dataObject.setLatencyMs(callLog.latencyMs());
        dataObject.setStatus(callLog.status());
        dataObject.setTenantId(callLog.tenantId());
        dataObject.setCreatedAt(callLog.createdAt());
        return dataObject;
    }
}
