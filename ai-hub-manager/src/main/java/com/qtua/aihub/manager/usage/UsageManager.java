package com.qtua.aihub.manager.usage;

import com.qtua.aihub.dal.mapper.UsageMapper;
import com.qtua.aihub.dal.model.UsageAggregateDO;
import com.qtua.aihub.manager.model.UsageAggregateBO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 用量聚合数据门面（REQ-4.3）。
 *
 * <p>读取 `v_usage_daily` 视图；视图口径与 call_logs 逐条一致。</p>
 */
@Component
public class UsageManager {

    private final UsageMapper usageMapper;

    /**
     * 构造门面。
     *
     * @param usageMapper 用量 Mapper
     */
    public UsageManager(UsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    /**
     * 查询用量聚合。
     *
     * @param from       起始日期
     * @param to         结束日期
     * @param tenantId   租户
     * @param projectId  项目
     * @param modelAlias 模型别名
     * @param keyId      Key 哈希
     * @return 聚合列表
     */
    public List<UsageAggregateBO> findUsage(LocalDate from, LocalDate to, String tenantId,
                                            String projectId, String modelAlias, String keyId) {
        return usageMapper.selectUsage(from, to, tenantId, projectId, modelAlias, keyId).stream()
                .map(this::toBO)
                .toList();
    }

    /**
     * 查询区间内实际使用的上游模型（缺价告警）。
     *
     * @param from      起始日期
     * @param to        结束日期
     * @param tenantId  租户
     * @param projectId 项目
     * @return 上游模型列表
     */
    public List<String> findUsedUpstreamModels(LocalDate from, LocalDate to, String tenantId,
                                               String projectId) {
        return usageMapper.selectUsedUpstreamModels(from, to, tenantId, projectId);
    }

    private UsageAggregateBO toBO(UsageAggregateDO dataObject) {
        return new UsageAggregateBO(dataObject.getUsageDate(), dataObject.getTenantId(),
                dataObject.getProjectId(), dataObject.getModelAlias(), dataObject.getKeyId(),
                dataObject.getRequests(), dataObject.getPromptTokens(),
                dataObject.getCompletionTokens(), dataObject.getTotalTokens(),
                dataObject.getCost());
    }
}
