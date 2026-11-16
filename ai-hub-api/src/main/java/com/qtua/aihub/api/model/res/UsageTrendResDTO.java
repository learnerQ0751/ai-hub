package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 用量趋势行（按天）。
 *
 * @param date        日期
 * @param requests    请求数
 * @param totalTokens 总 Token
 * @param cost        成本
 */
public record UsageTrendResDTO(@JsonProperty("date") String date, long requests,
                               @JsonProperty("total_tokens") long totalTokens,
                               BigDecimal cost) {
}
