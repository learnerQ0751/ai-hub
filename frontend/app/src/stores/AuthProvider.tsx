import { setUnauthorizedHandler } from '@/api/http';
import { loginSession, logoutSession, probeSession, type SessionUser } from '@/api/session';
import { AuthContext, type AuthStatus } from '@/stores/auth';
import type { ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('checking');
  const [session, setSession] = useState<SessionUser | null>(null);
  const [probeError, setProbeError] = useState<string | null>(null);

  const invalidate = useCallback(() => {
    setSession(null);
    setStatus('anonymous');
  }, []);

  const refresh = useCallback(async () => {
    try {
      const probe = await probeSession();
      setSession(probe.user ?? null);
      setStatus(probe.authenticated ? 'authenticated' : 'anonymous');
    } catch (cause) {
      setSession(null);
      setStatus('anonymous');
      setProbeError(cause instanceof Error ? cause.message : '无法检查登录状态');
    }
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const user = await loginSession(username, password);
    setSession(user);
    setStatus('authenticated');
    setProbeError(null);
    return user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutSession();
    } finally {
      invalidate();
    }
  }, [invalidate]);

  useEffect(() => {
    let cancelled = false;
    probeSession()
      .then((probe) => {
        if (cancelled) {
          return;
        }
        setSession(probe.user ?? null);
        setStatus(probe.authenticated ? 'authenticated' : 'anonymous');
      })
      .catch((cause: unknown) => {
        if (cancelled) {
          return;
        }
        setSession(null);
        setStatus('anonymous');
        setProbeError(cause instanceof Error ? cause.message : '无法检查登录状态');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(invalidate);
    return () => setUnauthorizedHandler(null);
  }, [invalidate]);

  const value = useMemo(
    () => ({ status, session, probeError, login, logout, invalidate, refresh }),
    [status, session, probeError, login, logout, invalidate, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
