import { useAuth } from '@/stores/auth';
import {
  ApartmentOutlined,
  ApiOutlined,
  AuditOutlined,
  BarChartOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ProfileOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { App, Button, Dropdown, Layout, Menu, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Sider, Content } = Layout;

/** 已接真实 console 接口的页面；其余仍为 mock/占位。 */
const REAL_DATA_KEYS = ['/prompts', '/logs', '/audit'];

const navItems: MenuProps['items'] = [
  { key: '/overview', icon: <DashboardOutlined />, label: '概览' },
  {
    type: 'group',
    label: '访问控制',
    children: [
      { key: '/keys', icon: <KeyOutlined />, label: '虚拟 Key' },
      { key: '/models', icon: <ApartmentOutlined />, label: '模型与部署' },
    ],
  },
  {
    type: 'group',
    label: '能力目录',
    children: [
      { key: '/mcp-servers', icon: <ApiOutlined />, label: 'MCP Servers' },
      { key: '/prompts', icon: <FileTextOutlined />, label: 'Prompt 模板' },
      { key: '/rag', icon: <DatabaseOutlined />, label: 'RAG 知识库' },
    ],
  },
  {
    type: 'group',
    label: '可观测',
    children: [
      { key: '/logs', icon: <ProfileOutlined />, label: '调用日志' },
      { key: '/usage', icon: <BarChartOutlined />, label: '用量与成本' },
      { key: '/audit', icon: <AuditOutlined />, label: '审计日志' },
    ],
  },
  {
    type: 'group',
    label: '调试',
    children: [{ key: '/playground', icon: <ExperimentOutlined />, label: 'Playground' }],
  },
  {
    type: 'group',
    label: '系统',
    children: [{ key: '/system', icon: <SettingOutlined />, label: '系统状态' }],
  },
];

interface AppShellProps {
  /** Design Board 预览：外壳可见，但不触发真实导航与账号操作。 */
  preview?: boolean;
  activeKey?: string;
}

export function AppShell({ preview = false, activeKey }: AppShellProps) {
  const { session, logout } = useAuth();
  const { message } = App.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const firstSegment = location.pathname.split('/').filter(Boolean)[0] ?? 'overview';
  const selectedKey = activeKey ?? `/${firstSegment}`;

  const handleLogout = async () => {
    await logout();
    message.success('已退出登录');
    navigate('/login', { replace: true });
  };

  const accountMenu: MenuProps = {
    items: [
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        onClick: () => void handleLogout(),
      },
    ],
  };

  const accountButton = (
    <Button type="text" className="app-header-account" icon={<UserOutlined />} disabled={preview}>
      {preview ? 'admin（演示）' : (session?.username ?? '未登录')}
    </Button>
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        width={232}
        collapsedWidth={0}
        breakpoint="lg"
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        theme="light"
        style={{ borderRight: '1px solid var(--color-border)' }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            height: 48,
            padding: '0 16px',
            borderBottom: '1px solid var(--color-border)',
          }}
        >
          <span style={{ fontSize: 15, fontWeight: 600 }}>ai-hub</span>
          <Tag style={{ marginInlineEnd: 0 }}>控制台</Tag>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={navItems}
          onClick={
            preview
              ? undefined
              : ({ key }) => {
                  navigate(key);
                  if (window.innerWidth < 992) {
                    setCollapsed(true);
                  }
                }
          }
          style={{ borderInlineEnd: 'none', paddingTop: 8 }}
        />
      </Sider>
      <Layout>
        <Header
          className="app-header"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            borderBottom: '1px solid var(--color-border)',
          }}
        >
          <Button
            type="text"
            aria-label={collapsed ? '展开导航' : '收起导航'}
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed((value) => !value)}
          />
          <Typography.Text type="secondary" className="app-header-title">
            AI 能力中台 · 管理控制台
          </Typography.Text>
          <div style={{ flex: 1 }} />
          <Tag color={REAL_DATA_KEYS.includes(selectedKey) ? 'blue' : 'default'}>
            {REAL_DATA_KEYS.includes(selectedKey) ? '真实接口' : 'MOCK'}
          </Tag>
          {preview ? (
            accountButton
          ) : (
            <Dropdown menu={accountMenu} placement="bottomRight">
              {accountButton}
            </Dropdown>
          )}
        </Header>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
