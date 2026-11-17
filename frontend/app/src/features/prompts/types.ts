/**
 * Prompt 模板版本视图，字段与后端 PromptResDTO 一致（API-PATHS §4.4）。
 * 注意：契约只提供 createdAt，没有 updatedAt。
 */
export type PromptStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface PromptVersion {
  name: string;
  version: number;
  status: PromptStatus;
  content: string;
  variables: string[];
  tenantId?: string | null;
  createdBy?: string | null;
  createdAt: string;
}

/** 与后端 PromptListResDTO 一致：每个模板的最新发布版本。 */
export interface PromptListResponse {
  prompts: PromptVersion[];
}

/** 与后端 PromptVersionListResDTO 一致：版本倒序。 */
export interface PromptVersionListResponse {
  versions: PromptVersion[];
}

export const PROMPT_STATUS_LABEL: Record<PromptStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
};

export const PROMPT_STATUS_DOT_COLOR: Record<PromptStatus, string> = {
  DRAFT: '#9ca3af',
  PUBLISHED: '#16a34a',
  ARCHIVED: '#d97706',
};

/** 后端枚举只有 DRAFT / PUBLISHED / ARCHIVED。 */
export function promptStatusOf(value: string): PromptStatus {
  return value === 'PUBLISHED' || value === 'ARCHIVED' ? value : 'DRAFT';
}
