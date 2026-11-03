package com.qtua.aihub.api.model.res;

import com.qtua.aihub.common.metering.TokenUsage;

/**
 * Prompt 对话响应（内部结果，Controller 回填治理上下文后返回 rawResponse）。
 *
 * @param rawResponse     上游原始响应 JSON
 * @param promptName      模板名
 * @param version         实际使用的版本
 * @param usage           Token 用量
 * @param deploymentModel 上游真实模型
 * @param servedAlias     服务别名
 * @param retryCount      重试次数
 * @param fallbackUsed    是否 fallback
 */
public record PromptChatResDTO(
        String rawResponse,
        String promptName,
        int version,
        TokenUsage usage,
        String deploymentModel,
        String servedAlias,
        int retryCount,
        boolean fallbackUsed) {
}
