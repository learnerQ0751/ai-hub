package com.qtua.aihub.common.metering;

/**
 * 请求前的 Token 用量估算。
 *
 * <p>用于 TPM 与预算预占：输入 token 按字符数估算，输出按 {@code max_tokens}
 * 上限估算；响应后用实际 {@link TokenUsage} 结算。</p>
 *
 * @param inputTokens    估算的输入 Token 数
 * @param maxOutputTokens 输出 Token 上限
 * @param totalTokens    估算总 Token 数
 */
public record TokenEstimate(int inputTokens, int maxOutputTokens, int totalTokens) {

    /**
     * 校验估算值非负。
     */
    public TokenEstimate {
        if (inputTokens < 0 || maxOutputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("Token estimate must not be negative");
        }
    }

    /**
     * 按输入与输出上限构造，总数为两者之和。
     *
     * @param inputTokens     输入 Token 估算
     * @param maxOutputTokens 输出 Token 上限
     * @return 估算值
     */
    public static TokenEstimate of(int inputTokens, int maxOutputTokens) {
        return new TokenEstimate(inputTokens, maxOutputTokens, inputTokens + maxOutputTokens);
    }
}
