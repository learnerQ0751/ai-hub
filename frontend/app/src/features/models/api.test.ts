import { afterEach, describe, expect, it, vi } from 'vitest';
import { createModel, deleteModel, updateModel } from './api';
import type { AliasUpsertPayload } from './form';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const model = {
  alias: 'chat',
  tenant_id: null,
  description: null,
  status: 'ACTIVE',
  fallbacks: [],
  input_price_per_million: null,
  output_price_per_million: null,
  created_at: '2026-11-18T02:00:00Z',
  updated_at: '2026-11-18T02:00:00Z',
  deployments: [],
};

const payload: AliasUpsertPayload = {
  alias: 'chat',
  status: 'ACTIVE',
  fallbacks: ['chat-backup'],
  description: '演示别名',
};

describe('models api', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('创建别名 POST /v1/admin/models，请求体与后端 AliasUpsertReqDTO 对齐', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(model));
    vi.stubGlobal('fetch', fetchMock);

    const created = await createModel(payload);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/v1/admin/models');
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('include');
    expect(JSON.parse(init.body as string)).toEqual({
      alias: 'chat',
      status: 'ACTIVE',
      fallbacks: ['chat-backup'],
      description: '演示别名',
    });
    expect(created.alias).toBe('chat');
  });

  it('更新别名 PUT /v1/admin/models/{alias}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(model));
    vi.stubGlobal('fetch', fetchMock);

    await updateModel('chat', payload);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/v1/admin/models/chat');
    expect(init.method).toBe('PUT');
  });

  it('删除别名 DELETE /v1/admin/models/{alias}，空响应体不报错', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(deleteModel('chat')).resolves.toBeUndefined();

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/v1/admin/models/chat');
    expect(init.method).toBe('DELETE');
  });

  it('409 conflict_error 映射为「同名模型已存在」，不吞成通用错误', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            error: {
              message: "模型别名 'chat' 已存在且配置不同，请使用更新接口",
              type: 'invalid_request_error',
              param: 'alias',
              code: 'conflict_error',
            },
          },
          409,
        ),
      ),
    );

    await expect(createModel(payload)).rejects.toMatchObject({
      status: 409,
      message: '同名模型已存在：chat',
    });
  });
});
