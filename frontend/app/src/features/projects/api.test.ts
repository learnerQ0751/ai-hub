import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchProjects } from './api';

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('projects api', () => {
  it('把 tenant/status/分页参数打到 /v1/admin/projects', async () => {
    const page = {
      items: [
        {
          tenant_id: 'default',
          project_id: 'demo',
          name: '演示项目',
          status: 'ACTIVE',
          created_at: '2026-11-17T08:00:00Z',
        },
      ],
      count: 1,
      has_more: false,
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchProjects({
      tenant_id: 'default',
      status: 'ACTIVE',
      limit: 20,
      offset: 40,
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const parsed = new URL(url, 'http://local');
    const query: Record<string, string> = {};
    parsed.searchParams.forEach((value, key) => {
      query[key] = value;
    });
    expect(parsed.pathname).toBe('/v1/admin/projects');
    expect(query).toEqual({
      tenant_id: 'default',
      status: 'ACTIVE',
      limit: '20',
      offset: '40',
    });
    expect(init.method).toBe('GET');
    expect(result).toEqual(page);
  });
});
