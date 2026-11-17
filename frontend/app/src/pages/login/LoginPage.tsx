import { ApiError } from '@/api/http';
import { useAuth } from '@/stores/auth';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Spin, Typography } from 'antd';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';

interface LoginFormValues {
  username: string;
  password: string;
}

function describeLoginError(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.status === 401) {
      return '用户名或密码错误';
    }
    if (cause.status === 403) {
      return '该账号没有控制台访问权限';
    }
    if (cause.status === 429) {
      return '尝试次数过多，请稍后再试';
    }
    if (cause.status === 0) {
      return '无法连接控制台服务，请检查网络或确认服务是否已启动';
    }
    return cause.message;
  }
  return cause instanceof Error ? cause.message : '登录失败，请重试';
}

export default function LoginPage() {
  const { status, probeError, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'checking') {
    return (
      <div className="login-page" aria-busy="true">
        <Spin size="large" />
      </div>
    );
  }

  if (status === 'authenticated') {
    return <Navigate to="/overview" replace />;
  }

  const handleSubmit = async (values: LoginFormValues) => {
    setSubmitting(true);
    setError(null);
    try {
      await login(values.username, values.password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/overview', { replace: true });
    } catch (cause) {
      setError(describeLoginError(cause));
    } finally {
      setSubmitting(false);
    }
  };

  const message = error ?? probeError;

  return (
    <div className="login-page">
      <Card className="login-card">
        <Typography.Title level={3} style={{ marginBottom: 4 }}>
          ai-hub 控制台
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 24 }}>
          AI 能力中台管理台 · 管理员账号登录
        </Typography.Paragraph>

        {message ? (
          <Alert type="error" showIcon message={message} style={{ marginBottom: 16 }} />
        ) : null}

        <Form<LoginFormValues>
          layout="vertical"
          onFinish={handleSubmit}
          requiredMark={false}
          disabled={submitting}
        >
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} autoComplete="username" placeholder="管理员用户名" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              autoComplete="current-password"
              placeholder="密码"
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 12 }}>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              登录
            </Button>
          </Form.Item>
        </Form>

        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          mock 演示账号：admin / admin123。前端不保存凭证；真实环境由后端下发 httpOnly 会话 cookie。
        </Typography.Text>
      </Card>
    </div>
  );
}
