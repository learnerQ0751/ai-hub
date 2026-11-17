import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import type { AdminKeyPage } from '@/features/keys/types';
import type { AdminLogPage, LogListQuery } from '@/features/logs/types';

/** 调用日志分页查询，筛选与分页全部走查询参数（§4.7）。 */
export async function fetchLogs(query: LogListQuery): Promise<AdminLogPage> {
  return request<AdminLogPage>(withQuery(endpoints.logs.list, { ...query }), { method: 'GET' });
}

export interface KeyAliasOption {
  value: string;
  label: string;
}

/**
 * 日志契约只认 key_id（Key 哈希），没有 alias 参数。
 * 这里用 /v1/admin/keys 的真实列表把 alias 映射成 key_hash，界面可按别名选，请求仍发 key_id。
 */
export async function fetchKeyAliasOptions(): Promise<KeyAliasOption[]> {
  const page = await request<AdminKeyPage>(
    withQuery(endpoints.keys.list, { limit: 200, offset: 0 }),
    { method: 'GET' },
  );
  return page.items.map((key) => ({
    value: key.key_hash,
    label: key.alias ? `${key.alias}（${key.key_hash}）` : key.key_hash,
  }));
}
