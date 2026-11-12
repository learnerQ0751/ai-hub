package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * embedding Token 用量（OpenAI 兼容）。
 *
 * @param promptTokens 输入 token
 * @param totalTokens  总 token
 */
public record EmbeddingUsageResDTO(
        @JsonProperty("prompt_tokens") long promptTokens,
        @JsonProperty("total_tokens") long totalTokens) {
}
