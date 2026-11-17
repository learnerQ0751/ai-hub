import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import type {
  PromptListResponse,
  PromptVersion,
  PromptVersionListResponse,
} from '@/features/prompts/types';

/**
 * Prompt 模板管理接口，真实 console 端点（API-PATHS §4.4）。
 * tenant_id 为可选查询参数：master 可为指定租户读取/发布。
 */
export async function fetchPrompts(tenantId?: string): Promise<PromptVersion[]> {
  const response = await request<PromptListResponse>(
    withQuery(endpoints.prompts.list, { tenant_id: tenantId }),
    { method: 'GET' },
  );
  return response.prompts;
}

export async function fetchPromptVersions(
  name: string,
  tenantId?: string,
): Promise<PromptVersion[]> {
  const response = await request<PromptVersionListResponse>(
    withQuery(endpoints.prompts.versions(name), { tenant_id: tenantId }),
    { method: 'GET' },
  );
  return response.versions;
}

/** 发布指定版本；发布旧版本即回滚。 */
export async function publishPromptVersion(
  name: string,
  version: number,
  tenantId?: string,
): Promise<PromptVersion> {
  return request<PromptVersion>(
    withQuery(endpoints.prompts.publish(name), { tenant_id: tenantId }),
    {
      method: 'POST',
      body: JSON.stringify({ version }),
    },
  );
}
