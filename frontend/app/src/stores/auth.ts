import type { SessionUser } from '@/api/session';
import { createContext, useContext } from 'react';

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  session: SessionUser | null;
  probeError: string | null;
  login: (username: string, password: string) => Promise<SessionUser>;
  logout: () => Promise<void>;
  /** 401/403 拦截后调用：只清前端状态，不碰凭证。 */
  invalidate: () => void;
  refresh: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}
