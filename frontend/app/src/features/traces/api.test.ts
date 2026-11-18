import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchTrace, fetchTraces } from './api';

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('traces api', () => {
  it('把冻结的筛选与分页参数打到 /v1/admin/traces', async () => {
    const page = { items: [], count: 0, has_more: false };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchTraces({
      tenant_id: 'qa',
      project_id: 'kb',
      key_id: 'hash-1',
      status: 429,
      from: '2026-11-01T00:00:00Z',
      to: '2026-11-02T00:00:00Z',
      limit: 20,
      offset: 40,
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const parsed = new URL(url, 'http://local');
    const query: Record<string, string> = {};
    parsed.searchParams.forEach((value, key) => {
      query[key] = value;
    });
    expect(parsed.pathname).toBe('/v1/admin/traces');
    expect(query).toEqual({
      tenant_id: 'qa',
      project_id: 'kb',
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

  it('按 trace_id 查询链路详情', async () => {
    const detail = { trace_id: 'tr-1', count: 0, events: [] };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(detail));
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchTrace('tr-1');
    expect(fetchMock.mock.calls[0][0]).toContain('/v1/admin/traces/tr-1');
    expect(result).toEqual(detail);
  });
});
