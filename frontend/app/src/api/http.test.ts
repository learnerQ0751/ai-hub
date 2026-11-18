import { ApiError, request, setUnauthorizedHandler } from '@/api/http';
import { afterEach, describe, expect, it, vi } from 'vitest';

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
  setUnauthorizedHandler(null);
});

describe('http 出口', () => {
  it('带 cookie 请求，且不附加 Authorization 头', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ authenticated: false }));
    vi.stubGlobal('fetch', fetchMock);

    await request('/v1/admin/session');

    const [, init] = fetchMock.mock.calls[0];
    expect(init.credentials).toBe('include');
    expect(new Headers(init.headers).has('Authorization')).toBe(false);
  });

  it('401 触发统一失效回调，并抛出可识别的错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 401)));
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    await expect(request('/v1/admin/keys')).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('403 也按会话失效处理', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 403)));
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    await expect(request('/v1/admin/keys')).rejects.toMatchObject({ status: 403 });
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('非 2xx 透传服务端错误体中的 message 与状态码', async () => {
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

    await expect(request('/v1/admin/models')).rejects.toMatchObject({
      status: 409,
      message: "模型别名 'chat' 已存在且配置不同，请使用更新接口",
    });
  });

  it('错误体不是 JSON 时回退为通用中文提示', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Bad Gateway', { status: 502 })));

    await expect(request('/v1/admin/models')).rejects.toMatchObject({
      status: 502,
      message: '请求失败（HTTP 502）',
    });
  });

  it('2xx 响应体不是 JSON 时给出中文错误，而不是裸 SyntaxError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>login</html>', { status: 200 })),
    );

    await expect(request('/v1/admin/models')).rejects.toMatchObject({
      status: 200,
      message: '响应格式不正确，请稍后重试',
    });
  });

  it('2xx 空响应体按成功返回 undefined（DELETE 无响应体）', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

    await expect(request('/v1/admin/models/chat', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('网络不可达返回 status 0 与中文提示', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed to fetch')));

    await expect(request('/v1/admin/session')).rejects.toMatchObject({
      status: 0,
      message: '无法连接控制台服务，请检查网络后重试',
    });
  });
});
