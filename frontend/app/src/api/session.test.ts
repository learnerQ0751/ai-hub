import { ApiError } from '@/api/http';
import { loginSession, logoutSession, probeSession, resetMockSession } from '@/api/session';
import { beforeEach, describe, expect, it } from 'vitest';

describe('mock 会话（cookie 语义）', () => {
  beforeEach(() => {
    resetMockSession();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('初始为未登录', async () => {
    await expect(probeSession()).resolves.toEqual({ authenticated: false });
  });

  it('错误凭证返回 401', async () => {
    await expect(loginSession('admin', 'wrong')).rejects.toBeInstanceOf(ApiError);
  });

  it('登录后探测为已登录，退出后回到未登录', async () => {
    const user = await loginSession('admin', 'admin123');
    expect(user.username).toBe('admin');

    const probe = await probeSession();
    expect(probe.authenticated).toBe(true);
    expect(probe.user?.username).toBe('admin');

    await logoutSession();
    await expect(probeSession()).resolves.toEqual({ authenticated: false });
  });

  it('前端不把会话写入 localStorage / sessionStorage', async () => {
    await loginSession('admin', 'admin123');

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('aihub');
    expect(document.cookie).not.toContain('token');
  });

  it('连续失败 5 次后返回限流提示', async () => {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      await expect(loginSession('admin', 'wrong')).rejects.toMatchObject({ status: 401 });
    }
    await expect(loginSession('admin', 'admin123')).rejects.toMatchObject({ status: 429 });
  });
});
