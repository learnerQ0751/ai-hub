import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchKeys, generateKey, setKeyBlocked } from './api';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('keys api', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('requests the paginated key list with server filters', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ items: [], count: 0, has_more: false }));
    vi.stubGlobal('fetch', fetchMock);

    await fetchKeys('ok', {
      tenant_id: 'platform',
      status: 'active',
      model: 'qwen-plus',
      limit: 20,
      offset: 40,
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/v1/admin/keys?');
    expect(url).toContain('tenant_id=platform');
    expect(url).toContain('status=active');
    expect(url).toContain('model=qwen-plus');
    expect(url).toContain('limit=20');
    expect(url).toContain('offset=40');
    expect(init.method).toBe('GET');
    expect(init.credentials).toBe('include');
  });

  it('posts block and unblock to the frozen paths', async () => {
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        jsonResponse({
          key_hash: 'abc',
          models: [],
          spend: 0,
          blocked: true,
          created_at: '2026-11-17T10:00:00Z',
        }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const key = await setKeyBlocked('abc', true);
    expect(key.blocked).toBe(true);
    expect(fetchMock.mock.calls[0][0]).toContain('/v1/admin/keys/abc/block');
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('POST');

    await setKeyBlocked('abc', false);
    expect(fetchMock.mock.calls[1][0]).toContain('/v1/admin/keys/abc/unblock');
  });

  it('creates a key through POST /v1/admin/keys and returns the one-time plaintext', async () => {
    const created = {
      key: 'sk-once-only-abc',
      models: ['chat'],
      spend: 0,
      blocked: false,
      alias: 'demo',
      tenant_id: 'platform',
      project_id: 'console-demo',
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(created));
    vi.stubGlobal('fetch', fetchMock);

    const result = await generateKey({
      alias: 'demo',
      tenant_id: 'platform',
      project_id: 'console-demo',
      models: ['chat'],
      duration: '7d',
      rpm_limit: 60,
      tpm_limit: 10000,
      max_budget: 5,
      budget_duration: 'monthly',
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/v1/admin/keys');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({
      alias: 'demo',
      tenant_id: 'platform',
      project_id: 'console-demo',
      models: ['chat'],
      duration: '7d',
      rpm_limit: 60,
      tpm_limit: 10000,
      max_budget: 5,
      budget_duration: 'monthly',
    });
    expect(result.key).toBe('sk-once-only-abc');
  });

  it('keeps the loading/empty/error demo states', async () => {
    await expect(fetchKeys('empty')).resolves.toEqual({ items: [], count: 0, has_more: false });
    await expect(fetchKeys('error')).rejects.toThrow('无法连接控制台服务');
  });
});
