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

  it('网络不可达返回 status 0 与中文提示', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed to fetch')));

    await expect(request('/v1/admin/session')).rejects.toMatchObject({
      status: 0,
      message: '无法连接控制台服务，请检查网络后重试',
    });
  });
});
