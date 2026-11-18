import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import { ADMIN_KEYS } from '@/features/keys/fixtures';
import type {
  AdminKey,
  AdminKeyPage,
  GenerateKeyInput,
  GeneratedKey,
  KeyListQuery,
} from '@/features/keys/types';
import type { MockMode } from '@/features/shared/mock';
import { delay, pending } from '@/features/shared/mock';

/**
 * §7.2 字段已由后端补齐（2026-11-17），数据源走真实 console 接口。
 * mock 只保留四态演示（loading / empty / error），不再作为默认数据源。
 */
const USE_MOCK = false;

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

/** 新建虚拟 Key；响应中的明文只返回这一次，前端不得持久化。 */
export async function generateKey(input: GenerateKeyInput): Promise<GeneratedKey> {
  return request<GeneratedKey>(endpoints.keys.create, {
    method: 'POST',
    body: JSON.stringify(input),
  });
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
