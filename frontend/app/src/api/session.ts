import { endpoints } from '@/api/endpoints';
import { ApiError, request } from '@/api/http';

/**
 * 控制台会话。
 * 真实形态：登录成功后由后端下发 httpOnly + Secure + SameSite=Lax 的会话 cookie；
 * 前端只通过 /v1/admin/session 探测「已登录 / 未登录」，凭证本体永远不在 JS 可见范围内。
 *
 * mock 只在开发环境作为默认（Board 截图与本地预览用），生产构建一律走真实会话；
 * 需要强制真实时可设 VITE_USE_MOCK_SESSION=false。
 */
export interface SessionUser {
  username: string;
  /** 后端会话端点返回的过期时间（ISO-8601）。 */
  expires_at?: string;
  displayName?: string;
  role?: 'admin';
}

export interface SessionProbe {
  authenticated: boolean;
  user?: SessionUser;
}

export const USE_MOCK_SESSION =
  import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_SESSION !== 'false';

const DEMO_USERNAME = 'admin';
const DEMO_PASSWORD = 'admin123';
const MAX_MOCK_FAILURES = 5;

let mockServerSession: SessionUser | null = null;
let mockFailedAttempts = 0;

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export async function probeSession(): Promise<SessionProbe> {
  if (!USE_MOCK_SESSION) {
    try {
      // 真实端点未登录返回 401；登录时直接返回 {"username","expires_at"}。
      const user = await request<SessionUser>(endpoints.session.probe);
      return { authenticated: true, user };
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
        return { authenticated: false };
      }
      throw cause;
    }
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
