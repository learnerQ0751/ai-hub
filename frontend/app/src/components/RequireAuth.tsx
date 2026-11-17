import { useAuth } from '@/stores/auth';
import { Spin } from 'antd';
import { Navigate, Outlet, useLocation } from 'react-router-dom';

export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'checking') {
    return (
      <div className="auth-checking" aria-busy="true" aria-label="正在检查登录状态">
        <Spin size="large" />
      </div>
    );
  }

  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
