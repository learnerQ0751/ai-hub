package com.qtua.aihub.manager.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 用量聚合业务对象（REQ-4.3）。
 *
 * @param date             日期
 * @param tenantId         租户
 * @param projectId        项目
 * @param modelAlias       模型别名
 * @param keyId            Key 哈希
 * @param requests         请求数
 * @param promptTokens     输入 Token
 * @param completionTokens 输出 Token
 * @param totalTokens      总 Token
 * @param cost             成本
 */
public record UsageAggregateBO(LocalDate date, String tenantId, String projectId,
                               String modelAlias, String keyId, long requests,
                               long promptTokens, long completionTokens, long totalTokens,
                               BigDecimal cost) {
}
