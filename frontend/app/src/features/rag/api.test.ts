import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchRagChunks } from './api';

const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('rag chunks api', () => {
  it('按文档与项目分页拉取切片，docId 做 URL 编码', async () => {
    const page = {
      items: [
        {
          chunk_id: 'c-1',
          doc_id: 'doc/1',
          project_id: 'demo',
          chunk_index: 0,
          content: '片段',
          char_count: 2,
          source: 'demo.md',
          index_status: 'READY',
          created_at: '2026-11-17T08:00:00Z',
        },
      ],
      count: 1,
      has_more: false,
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchRagChunks('doc/1', 'demo', 20, 40);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const parsed = new URL(url, 'http://local');
    const query: Record<string, string> = {};
    parsed.searchParams.forEach((value, key) => {
      query[key] = value;
    });
    expect(parsed.pathname).toBe('/v1/rag/documents/doc%2F1/chunks');
    expect(query).toEqual({ project_id: 'demo', limit: '20', offset: '40' });
    expect(init.method).toBe('GET');
    expect(result).toEqual(page);
  });
});
