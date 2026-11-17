import { API_BASE_URL, endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import type { UsageQuery, UsageResult } from '@/features/usage/types';

/** 用量聚合与趋势；筛选参数全部走查询串（§4.7）。 */
export async function fetchUsage(query: UsageQuery = {}): Promise<UsageResult> {
  return request<UsageResult>(withQuery(endpoints.usage.query, { ...query }), { method: 'GET' });
}

/** CSV 导出地址：由浏览器直接下载，cookie 会话随请求携带。 */
export function usageExportUrl(query: UsageQuery = {}): string {
  return `${API_BASE_URL}${withQuery(endpoints.usage.export, { ...query })}`;
}
