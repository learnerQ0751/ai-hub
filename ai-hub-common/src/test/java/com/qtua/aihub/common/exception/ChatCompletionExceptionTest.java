package com.qtua.aihub.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对外错误文案语言约束：message 面向中文用户，type/param/code 保持协议英文。
 */
class ChatCompletionExceptionTest {

    @Test
    void user_facing_messages_are_chinese() {
        assertThat(ChatCompletionException.invalidModel("x").getMessage()).contains("模型");
        assertThat(ChatCompletionException.invalidApiKey().getMessage()).contains("鉴权");
        assertThat(ChatCompletionException.blockedApiKey().getMessage()).contains("封禁");
        assertThat(ChatCompletionException.expiredApiKey().getMessage()).contains("过期");
        assertThat(ChatCompletionException.masterKeyRequired().getMessage()).contains("master key");
        assertThat(ChatCompletionException.keyModelAccessDenied("x").getMessage()).contains("无权");
        assertThat(ChatCompletionException.rateLimitExceeded("rpm").getMessage()).contains("限流");
        assertThat(ChatCompletionException.budgetExceeded().getMessage()).contains("预算");
        assertThat(ChatCompletionException.upstreamAuthError(new RuntimeException()).getMessage())
                .contains("上游");
        assertThat(ChatCompletionException.upstreamTimeout(new RuntimeException()).getMessage())
                .contains("超时");
        assertThat(ChatCompletionException.upstreamError("上游失败", null).getMessage())
                .contains("上游");
        assertThat(ChatCompletionException.upstreamRateLimited(new RuntimeException()).getMessage())
                .contains("上游");
        assertThat(ChatCompletionException.upstreamClientError(400, null).getMessage())
                .contains("上游");
        assertThat(ChatCompletionException.apiKeyNotFound().getMessage()).contains("不存在");
    }

    @Test
    void protocol_fields_stay_english() {
        ChatCompletionException exception = ChatCompletionException.invalidModel("x");

        assertThat(exception.getType()).isEqualTo("invalid_request_error");
        assertThat(exception.getParam()).isEqualTo("model");
        assertThat(exception.getCode()).isEqualTo("400");
        assertThat(exception.getStatus().value()).isEqualTo(400);
    }
}
