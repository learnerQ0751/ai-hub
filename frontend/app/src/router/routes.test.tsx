import { loginSession, resetMockSession } from '@/api/session';
import { AppRouter } from '@/router';
import { AuthProvider } from '@/stores/AuthProvider';
import { d1Theme } from '@/styles/theme';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { App as AntdApp, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

const routes: Array<[string, string]> = [
  ['/overview', '概览'],
  ['/keys', '虚拟 Key'],
  ['/models', '模型与部署'],
  ['/mcp-servers', 'MCP Servers'],
  ['/prompts', 'Prompt 模板'],
  ['/rag', 'RAG 知识库'],
  ['/logs', '调用日志'],
  ['/usage', '用量与成本'],
  ['/audit', '审计日志'],
  ['/playground', 'Playground'],
  ['/system', '系统状态'],
];

function renderAt(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <ConfigProvider theme={d1Theme}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[path]}>
            <AuthProvider>
              <AppRouter />
            </AuthProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  );
}

beforeEach(() => {
  resetMockSession();
});

describe('已登录路由', () => {
  beforeEach(async () => {
    await loginSession('admin', 'admin123');
  });

  it.each(routes)('渲染 %s 的页面标题', async (path, title) => {
    renderAt(path);
    const heading = await screen.findByRole('heading', { level: 1, name: title });
    expect(heading).toBeTruthy();
  });
});

describe('登录与会话', () => {
  it('未登录访问受保护页面会跳到登录页', async () => {
    renderAt('/keys');
    expect(await screen.findByText('ai-hub 控制台')).toBeTruthy();
  });

  it('用 mock 管理员账号登录后进入概览', async () => {
    renderAt('/login');
    expect(await screen.findByText('ai-hub 控制台')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'admin123' } });
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));

    const heading = await screen.findByRole(
      'heading',
      { level: 1, name: '概览' },
      { timeout: 4000 },
    );
    expect(heading).toBeTruthy();
  });

  it('登录失败显示可读的错误文案', async () => {
    renderAt('/login');
    expect(await screen.findByText('ai-hub 控制台')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));

    expect(await screen.findByText('用户名或密码错误', {}, { timeout: 4000 })).toBeTruthy();
  });

  it('登录后可从账号菜单退出并回到登录页', async () => {
    await loginSession('admin', 'admin123');
    renderAt('/overview');

    const account = await screen.findByRole('button', { name: /admin/ });
    fireEvent.mouseEnter(account);
    const logout = await screen.findByText('退出登录');
    fireEvent.click(logout);

    expect(await screen.findByText('ai-hub 控制台', {}, { timeout: 4000 })).toBeTruthy();
  });
});
