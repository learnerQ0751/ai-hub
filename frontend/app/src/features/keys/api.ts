import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import { ADMIN_KEYS } from '@/features/keys/fixtures';
import type { AdminKey, AdminKeyPage, KeyListQuery } from '@/features/keys/types';
import type { MockMode } from '@/features/shared/mock';
import { delay, pending } from '@/features/shared/mock';

/**
 * 后端 §7.2 字段（alias、rpm_limit、tpm_limit、metadata、budget_duration）尚未实现，
 * 暂时由 fixtures 顶替数据源；置为 false 即走真实 console 接口，页面与类型不用改。
 */
const USE_MOCK = true;

let store: AdminKey[] = ADMIN_KEYS.map((key) => ({ ...key, models: [...key.models] }));

export function resetKeysMockStore(): void {
  store = ADMIN_KEYS.map((key) => ({ ...key, models: [...key.models] }));
}

function clone(key: AdminKey): AdminKey {
  return { ...key, models: [...key.models] };
}

function pageOf(items: AdminKey[], limit: number, offset: number): AdminKeyPage {
  const page = items.slice(offset, offset + limit);
  return { items: page, count: page.length, has_more: items.length > offset + limit };
}

export async function fetchKeys(
  mode: MockMode = 'ok',
  query: KeyListQuery = {},
): Promise<AdminKeyPage> {
  if (mode === 'loading') {
    return pending<AdminKeyPage>();
  }
  if (mode === 'error') {
    await delay();
    throw new Error('无法连接控制台服务（mock 故障）');
  }
  if (mode === 'empty') {
    await delay();
    return { items: [], count: 0, has_more: false };
  }

  const limit = query.limit ?? 50;
  const offset = query.offset ?? 0;

  if (!USE_MOCK) {
    return request<AdminKeyPage>(withQuery(endpoints.keys.list, { ...query, limit, offset }), {
      method: 'GET',
    });
  }

  await delay();
  const matched = store.filter(
    (key) =>
      (!query.tenant_id || key.tenant_id === query.tenant_id) &&
      (!query.project_id || key.project_id === query.project_id),
  );
  return pageOf(matched.map(clone), limit, offset);
}

export async function setKeyBlocked(keyHash: string, blocked: boolean): Promise<AdminKey> {
  if (!USE_MOCK) {
    return request<AdminKey>(
      blocked ? endpoints.keys.block(keyHash) : endpoints.keys.unblock(keyHash),
      { method: 'POST' },
    );
  }

  await delay(400);
  const index = store.findIndex((key) => key.key_hash === keyHash);
  if (index < 0) {
    throw new Error('Key 不存在（mock）');
  }
  const updated: AdminKey = { ...store[index], blocked };
  store[index] = updated;
  return clone(updated);
}
