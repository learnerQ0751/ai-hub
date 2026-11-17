import { endpoints } from '@/api/endpoints';
import { ApiError, request } from '@/api/http';

/**
 * 控制台会话。
 * 真实形态：登录成功后由后端下发 httpOnly + Secure + SameSite=Lax 的会话 cookie；
 * 前端只通过 /v1/admin/session 探测「已登录 / 未登录」，凭证本体永远不在 JS 可见范围内。
 *
 * 契约未冻结，先走 mock：mock 会话存在模块内存里，模拟服务端 cookie 会话；
 * 不写 localStorage / sessionStorage，也不保存任何 token。
 * 代价：mock 下刷新页面会回到未登录，真实 cookie 会保持。
 */
export interface SessionUser {
  username: string;
  displayName: string;
  role: 'admin';
}

export interface SessionProbe {
  authenticated: boolean;
  user?: SessionUser;
}

const USE_MOCK_SESSION = import.meta.env.VITE_USE_MOCK_SESSION !== 'false';

const DEMO_USERNAME = 'admin';
const DEMO_PASSWORD = 'admin123';
const MAX_MOCK_FAILURES = 5;

let mockServerSession: SessionUser | null = null;
let mockFailedAttempts = 0;

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export async function probeSession(): Promise<SessionProbe> {
  if (!USE_MOCK_SESSION) {
    return request<SessionProbe>(endpoints.session.probe);
  }
  await delay(300);
  return mockServerSession
    ? { authenticated: true, user: mockServerSession }
    : { authenticated: false };
}

export async function loginSession(username: string, password: string): Promise<SessionUser> {
  if (!USE_MOCK_SESSION) {
    return request<SessionUser>(endpoints.session.login, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  }

  await delay(600);
  if (mockFailedAttempts >= MAX_MOCK_FAILURES) {
    throw new ApiError(429, '尝试次数过多，请 30 秒后再试');
  }
  if (username !== DEMO_USERNAME || password !== DEMO_PASSWORD) {
    mockFailedAttempts += 1;
    throw new ApiError(401, '用户名或密码错误');
  }

  mockFailedAttempts = 0;
  mockServerSession = { username, displayName: '管理员', role: 'admin' };
  return mockServerSession;
}

export async function logoutSession(): Promise<void> {
  if (!USE_MOCK_SESSION) {
    return request<void>(endpoints.session.logout, { method: 'DELETE' });
  }
  await delay(200);
  mockServerSession = null;
}

/** 仅测试使用：清空 mock 服务端会话与失败计数。 */
export function resetMockSession(): void {
  mockServerSession = null;
  mockFailedAttempts = 0;
}
