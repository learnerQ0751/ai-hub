import { beforeEach, describe, expect, it } from 'vitest';
import { resetKeysMockStore, fetchKeys, setKeyBlocked } from './api';

describe('keys mock data source', () => {
  beforeEach(() => {
    resetKeysMockStore();
  });

  it('returns the paginated admin key shape', async () => {
    const page = await fetchKeys('ok', { limit: 3, offset: 0 });
    expect(page.items).toHaveLength(3);
    expect(page.count).toBe(3);
    expect(page.has_more).toBe(true);
    expect(page.items[0]).toHaveProperty('key_hash');
    expect(page.items[0]).toHaveProperty('models');
  });

  it('filters by tenant on the data source', async () => {
    const page = await fetchKeys('ok', { tenant_id: 'platform' });
    expect(page.items.map((key) => key.alias)).toEqual(['研发-沙箱', '向量化任务']);
  });

  it('returns empty and error states', async () => {
    await expect(fetchKeys('empty')).resolves.toEqual({ items: [], count: 0, has_more: false });
    await expect(fetchKeys('error')).rejects.toThrow('mock 故障');
  });

  it('blocks and unblocks a key in the mock store', async () => {
    const before = await fetchKeys('ok');
    const target = before.items.find((key) => !key.blocked);
    expect(target).toBeDefined();

    const blocked = await setKeyBlocked(target!.key_hash, true);
    expect(blocked.blocked).toBe(true);

    const after = await fetchKeys('ok');
    expect(after.items.find((key) => key.key_hash === target!.key_hash)?.blocked).toBe(true);

    const unblocked = await setKeyBlocked(target!.key_hash, false);
    expect(unblocked.blocked).toBe(false);
  });
});
