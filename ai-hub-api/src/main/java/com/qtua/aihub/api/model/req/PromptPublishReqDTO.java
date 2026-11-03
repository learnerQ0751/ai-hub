package com.qtua.aihub.api.model.req;

/**
 * 发布 Prompt 版本请求。
 *
 * @param version 目标版本（发布旧版本即回滚）
 */
public record PromptPublishReqDTO(Integer version) {
}
