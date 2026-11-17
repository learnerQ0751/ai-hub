import { fetchKeyAliasOptions, fetchLogs } from '@/features/logs/api';
import { afterEach, describe, expect, it, vi } from 'vitest';

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('logs 真实接口', () => {
  it('筛选与分页参数全部打到 /v1/admin/logs', async () => {
    const page = { items: [], count: 0, has_more: false };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchLogs({
      tenant_id: 'qa',
      project_id: 'kb',
      model: 'deepseek-chat',
      key_id: 'hash-1',
      status: 429,
      from: '2026-11-01T00:00:00Z',
      to: '2026-11-02T00:00:00Z',
      limit: 20,
      offset: 40,
    });

    const [url, init] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://local');
    const query: Record<string, string> = {};
    parsed.searchParams.forEach((value, key) => {
      query[key] = value;
    });
    expect(parsed.pathname).toBe('/v1/admin/logs');
    expect(query).toEqual({
      tenant_id: 'qa',
      project_id: 'kb',
      model: 'deepseek-chat',
      key_id: 'hash-1',
      status: '429',
      from: '2026-11-01T00:00:00Z',
      to: '2026-11-02T00:00:00Z',
      limit: '20',
      offset: '40',
    });
    expect(init.method).toBe('GET');
    expect(result).toEqual(page);
  });

  it('未设置的筛选不会出现在查询串里', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ items: [], count: 0, has_more: false }));
    vi.stubGlobal('fetch', fetchMock);

    await fetchLogs({ limit: 20, offset: 0 });

    expect(fetchMock.mock.calls[0][0]).toBe('/v1/admin/logs?limit=20&offset=0');
  });

  it('Key 别名选项由真实 Key 列表映射到 key_hash', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        items: [
          {
            key_hash: 'hash-a',
            alias: '研发-沙箱',
            models: [],
            spend: 0,
            blocked: false,
            created_at: '2026-11-01T00:00:00Z',
          },
          {
            key_hash: 'hash-b',
            models: [],
            spend: 0,
            blocked: false,
            created_at: '2026-11-01T00:00:00Z',
          },
        ],
        count: 2,
        has_more: false,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const options = await fetchKeyAliasOptions();

    expect(fetchMock.mock.calls[0][0]).toBe('/v1/admin/keys?limit=200&offset=0');
    expect(options).toEqual([
      { value: 'hash-a', label: '研发-沙箱（hash-a）' },
      { value: 'hash-b', label: 'hash-b' },
    ]);
  });
});
