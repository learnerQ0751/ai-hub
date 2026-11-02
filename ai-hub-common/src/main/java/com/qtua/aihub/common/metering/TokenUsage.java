package com.qtua.aihub.common.metering;

/**
 * 一次调用的 Token 用量。
 *
 * <p>由上游响应中的 {@code usage} 字段解析而来；缺失时统一使用 {@link #ZERO}。
 * 负数属于非法用量，构造时直接拒绝，避免污染计量与预算。</p>
 *
 * @param promptTokens     输入 Token 数
 * @param completionTokens 输出 Token 数
 * @param totalTokens      总 Token 数（以上游返回为准）
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    /** 零用量：上游未返回 usage 或流式尚未结算时使用。 */
    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    /**
     * 校验用量非负。
     */
    public TokenUsage {
        if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("Token usage must not be negative");
        }
    }

    /**
     * 按输入/输出 Token 构造，总数为两者之和。
     *
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     * @return 用量
     */
    public static TokenUsage of(int promptTokens, int completionTokens) {
        return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);
    }
}
