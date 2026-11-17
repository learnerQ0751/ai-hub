import { fetchPromptVersions, fetchPrompts, publishPromptVersion } from '@/features/prompts/api';
import { afterEach, describe, expect, it, vi } from 'vitest';

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

const prompt = {
  name: 'greeting',
  version: 2,
  status: 'DRAFT',
  content: '你好 {{name}}',
  variables: ['name'],
  tenantId: 'qa',
  createdBy: 'qtua',
  createdAt: '2026-11-17T01:00:00Z',
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('prompts 真实接口', () => {
  it('列表带 tenant_id，并解包 prompts 字段', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ prompts: [prompt] }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchPrompts('qa');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/v1/prompts?tenant_id=qa');
    expect(init.method).toBe('GET');
    expect(result).toEqual([prompt]);
  });

  it('版本历史走 /versions 并带 tenant_id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ versions: [prompt] }));
    vi.stubGlobal('fetch', fetchMock);

    await fetchPromptVersions('greeting', 'qa');

    expect(fetchMock.mock.calls[0][0]).toBe('/v1/prompts/greeting/versions?tenant_id=qa');
  });

  it('发布请求把 version 放 JSON body，tenant_id 放查询参数', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(prompt));
    vi.stubGlobal('fetch', fetchMock);

    await publishPromptVersion('greeting', 2, 'qa');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/v1/prompts/greeting/publish?tenant_id=qa');
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"version":2}');
    expect(new Headers(init.headers).get('Content-Type')).toContain('application/json');
  });
});
