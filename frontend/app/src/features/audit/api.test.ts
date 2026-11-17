import { fetchAuditLogs } from '@/features/audit/api';
import { afterEach, describe, expect, it, vi } from 'vitest';

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('audit 真实接口', () => {
  it('使用契约路径 /v1/admin/audit-logs，并带 action/target/from/to/limit/offset', async () => {
    const page = { items: [], count: 0, has_more: false };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchAuditLogs({
      action: 'PROMPT_PUBLISH',
      target: 'greeting',
      from: '2026-11-01T00:00:00Z',
      to: '2026-11-02T00:00:00Z',
      limit: 50,
      offset: 0,
    });

    const [url, init] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://local');
    const query: Record<string, string> = {};
    parsed.searchParams.forEach((value, key) => {
      query[key] = value;
    });
    expect(parsed.pathname).toBe('/v1/admin/audit-logs');
    // 会话 cookie 的 Path 是 /v1/admin，管理面端点必须落在这个前缀下。
    expect(parsed.pathname.startsWith('/v1/admin/')).toBe(true);
    expect(query).toEqual({
      action: 'PROMPT_PUBLISH',
      target: 'greeting',
      from: '2026-11-01T00:00:00Z',
      to: '2026-11-02T00:00:00Z',
      limit: '50',
      offset: '0',
    });
    expect(init.method).toBe('GET');
    expect(result).toEqual(page);
  });
});
