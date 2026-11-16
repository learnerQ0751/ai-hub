package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 用量查询响应（REQ-4.3）。
 *
 * @param from           起始日期
 * @param to             结束日期
 * @param tenantId       生效租户（null = 全部）
 * @param projectId      生效项目（null = 全部）
 * @param totals         汇总
 * @param items          明细
 * @param trend          按天趋势
 * @param unpricedModels 缺价上游模型（单列告警）
 */
public record UsageResDTO(@JsonProperty("from") String from,
                          @JsonProperty("to") String to,
                          @JsonProperty("tenant_id") String tenantId,
                          @JsonProperty("project_id") String projectId,
                          UsageTotalsResDTO totals,
                          List<UsageItemResDTO> items,
                          List<UsageTrendResDTO> trend,
                          @JsonProperty("unpriced_models") List<String> unpricedModels) {

    /**
     * 防御性复制集合。
     */
    public UsageResDTO {
        items = Objects.isNull(items) ? List.of() : List.copyOf(items);
        trend = Objects.isNull(trend) ? List.of() : List.copyOf(trend);
        unpricedModels = Objects.isNull(unpricedModels) ? List.of() : List.copyOf(unpricedModels);
    }
}
