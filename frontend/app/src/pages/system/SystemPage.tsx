import { PageHeader } from '@/components/PageHeader';
import { API_BASE_URL, GATEWAY_BASE_URL, endpoints } from '@/api/endpoints';
import { request } from '@/api/http';
import { fetchKeys } from '@/features/keys/api';
import { fetchMcpRuntimeStats, fetchMcpServers } from '@/features/mcp/api';
import { fetchModels } from '@/features/models/api';
import { useAuth } from '@/stores/auth';
import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Descriptions, Empty, Row, Skeleton, Tag } from 'antd';
import { useMemo } from 'react';

interface HealthResponse {
  status: string;
  [key: string]: unknown;
}

async function fetchGatewayHealth(): Promise<HealthResponse> {
  const response = await fetch(`${GATEWAY_BASE_URL}/actuator/health`);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return (await response.json()) as HealthResponse;
}

function HealthBadge({ status }: { status?: string }) {
  if (!status) {
    return <Tag>未知</Tag>;
  }
  return (
    <Tag color={status.toUpperCase() === 'UP' ? 'success' : 'error'} style={{ marginInlineEnd: 0 }}>
      {status.toUpperCase()}
    </Tag>
  );
}

export default function SystemPage() {
  const { session } = useAuth();

  const consoleHealthQuery = useQuery({
    queryKey: ['system', 'console-health'],
    queryFn: () => request<HealthResponse>(endpoints.health.console, { method: 'GET' }),
    retry: false,
    refetchInterval: 30_000,
  });

  const gatewayHealthQuery = useQuery({
    queryKey: ['system', 'gateway-health'],
    queryFn: fetchGatewayHealth,
    // 没有独立数据面基址时不再探测：同源 /actuator/health 指向 console，探测会给出假阳性。
    enabled: Boolean(GATEWAY_BASE_URL),
    retry: false,
    refetchInterval: 30_000,
  });

  const mcpQuery = useQuery({
    queryKey: ['system', 'mcp'],
    queryFn: () => fetchMcpServers('ok'),
    retry: false,
  });

  const mcpStatsQuery = useQuery({
    queryKey: ['system', 'mcp-stats'],
    queryFn: () => fetchMcpRuntimeStats('ok'),
    retry: false,
  });

  const keysQuery = useQuery({
    queryKey: ['system', 'keys'],
    queryFn: () => fetchKeys('ok', { limit: 200, offset: 0 }),
    retry: false,
  });

  const modelsQuery = useQuery({
    queryKey: ['system', 'models'],
    queryFn: () => fetchModels('ok'),
    retry: false,
  });

  const mcpServers = mcpQuery.data ?? [];
  const healthy = mcpServers.filter((server) => server.status === 'UP').length;

  const refreshAll = () => {
    void consoleHealthQuery.refetch();
    void gatewayHealthQuery.refetch();
    void mcpQuery.refetch();
    void mcpStatsQuery.refetch();
    void keysQuery.refetch();
    void modelsQuery.refetch();
  };

  const gatewayBaseLabel = useMemo(
    () => (GATEWAY_BASE_URL ? GATEWAY_BASE_URL : '同源（由反向代理转发）'),
    [],
  );
  const consoleBaseLabel = useMemo(
    () => (API_BASE_URL ? API_BASE_URL : '同源（由反向代理转发）'),
    [],
  );

  return (
    <>
      <PageHeader
        title="系统状态"
        section="系统"
        description="console 与 gateway 健康、MCP 运行时计数与当前部署口径；健康检查每 30 秒自动刷新。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={refreshAll}>
            刷新
          </Button>
        }
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card
            size="small"
            title="console（控制面）"
            extra={<HealthBadge status={consoleHealthQuery.data?.status} />}
          >
            {consoleHealthQuery.isPending ? (
              <Skeleton active paragraph={{ rows: 2 }} />
            ) : consoleHealthQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="console 健康检查失败"
                description={
                  consoleHealthQuery.error instanceof Error
                    ? consoleHealthQuery.error.message
                    : '未知错误'
                }
              />
            ) : (
              <Descriptions
                size="small"
                column={1}
                items={[
                  {
                    key: 'status',
                    label: '状态',
                    children: <span className="mono">{consoleHealthQuery.data?.status}</span>,
                  },
                  {
                    key: 'base',
                    label: '接口基址',
                    children: <span className="mono">{consoleBaseLabel}</span>,
                  },
                  {
                    key: 'endpoint',
                    label: '健康检查',
                    children: <span className="mono">GET {endpoints.health.console}</span>,
                  },
                ]}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            size="small"
            title="gateway（数据面）"
            extra={<HealthBadge status={gatewayHealthQuery.data?.status} />}
          >
            {!GATEWAY_BASE_URL ? (
              <Alert
                type="info"
                showIcon
                message="未配置独立数据面基址"
                description="部署时用 VITE_GATEWAY_BASE_URL 指向 gateway，或由反向代理提供独立的健康检查路径。"
              />
            ) : gatewayHealthQuery.isPending ? (
              <Skeleton active paragraph={{ rows: 2 }} />
            ) : gatewayHealthQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="gateway 健康检查失败"
                description={
                  gatewayHealthQuery.error instanceof Error
                    ? gatewayHealthQuery.error.message
                    : '未知错误'
                }
              />
            ) : (
              <Descriptions
                size="small"
                column={1}
                items={[
                  {
                    key: 'status',
                    label: '状态',
                    children: <span className="mono">{gatewayHealthQuery.data?.status}</span>,
                  },
                  {
                    key: 'base',
                    label: '接口基址',
                    children: <span className="mono">{gatewayBaseLabel}</span>,
                  },
                  {
                    key: 'endpoint',
                    label: '健康检查',
                    children: <span className="mono">GET /actuator/health</span>,
                  },
                ]}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card size="small" title="MCP 运行时">
            <Descriptions
              size="small"
              column={1}
              items={[
                {
                  key: 'stdio',
                  label: '活跃 stdio 进程',
                  children: (
                    <span className="num">{mcpStatsQuery.data?.activeStdioProcesses ?? '—'}</span>
                  ),
                },
                {
                  key: 'probe',
                  label: '进行中探活',
                  children: (
                    <span className="num">{mcpStatsQuery.data?.inFlightProbeHttp ?? '—'}</span>
                  ),
                },
                {
                  key: 'calls',
                  label: '进行中工具调用',
                  children: (
                    <span className="num">{mcpStatsQuery.data?.inFlightToolCalls ?? '—'}</span>
                  ),
                },
                {
                  key: 'servers',
                  label: 'Server 健康',
                  children: (
                    <span className="num">
                      {mcpQuery.isSuccess ? `${healthy} / ${mcpServers.length}` : '—'}
                    </span>
                  ),
                },
              ]}
            />
            {mcpQuery.isSuccess && mcpServers.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有注册 MCP Server" />
            ) : null}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card size="small" title="当前会话与部署口径">
            <Descriptions
              size="small"
              column={1}
              items={[
                { key: 'user', label: '登录账号', children: session?.username ?? '未登录' },
                {
                  key: 'expires',
                  label: '会话过期时间',
                  children: <span className="num">{session?.expires_at ?? '—'}</span>,
                },
                {
                  key: 'keys',
                  label: 'Key 数量',
                  children: (
                    <span className="num">{keysQuery.isSuccess ? keysQuery.data.count : '—'}</span>
                  ),
                },
                {
                  key: 'models',
                  label: '模型别名',
                  children: (
                    <span className="num">
                      {modelsQuery.isSuccess ? modelsQuery.data.length : '—'}
                    </span>
                  ),
                },
                {
                  key: 'mode',
                  label: '前端构建模式',
                  children: <span className="mono">{import.meta.env.MODE}</span>,
                },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
        数据面与控制面分离部署：前端通过环境变量 VITE_API_BASE_URL / VITE_GATEWAY_BASE_URL
        指向各自基址，留空即为同源反代。
      </p>
    </>
  );
}
