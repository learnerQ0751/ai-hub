package com.qtua.aihub.common.prompt;

/**
 * Prompt 模板版本状态。
 *
 * <p>DRAFT 草稿 → PUBLISHED 已发布 → ARCHIVED 归档；同一 name 仅一个 PUBLISHED。
 * 发布旧版本（回滚）会把当前 PUBLISHED 归档后再发布目标版本。</p>
 */
public enum PromptStatus {

    /** 草稿，未被发布。 */
    DRAFT,

    /** 当前发布版本。 */
    PUBLISHED,

    /** 历史发布版本（被新发布替代）。 */
    ARCHIVED
}
