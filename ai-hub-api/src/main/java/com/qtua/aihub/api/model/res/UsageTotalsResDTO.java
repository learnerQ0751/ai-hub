package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 用量汇总（REQ-4.3）。
 *
 * @param requests         请求数
 * @param promptTokens     输入 Token
 * @param completionTokens 输出 Token
 * @param totalTokens      总 Token
 * @param cost             总成本
 */
public record UsageTotalsResDTO(long requests,
                                @JsonProperty("prompt_tokens") long promptTokens,
                                @JsonProperty("completion_tokens") long completionTokens,
                                @JsonProperty("total_tokens") long totalTokens,
                                BigDecimal cost) {
}
