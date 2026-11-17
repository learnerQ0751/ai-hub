import { Chart } from '@/components/Chart';
import { PageHeader } from '@/components/PageHeader';
import { fetchKeys } from '@/features/keys/api';
import { keyStatus } from '@/features/keys/types';
import { fetchLogs } from '@/features/logs/api';
import { formatCost, formatLatency, logStatusTone } from '@/features/logs/types';
import { fetchMcpRuntimeStats, fetchMcpServers } from '@/features/mcp/api';
import {
  formatDateTime as formatMcpDateTime,
  MCP_STATUS_DOT_COLOR,
  MCP_STATUS_LABEL,
} from '@/features/mcp/types';
import { fetchModels } from '@/features/models/api';
import { formatDateTime, formatNumber } from '@/features/shared/format';
import { fetchUsage } from '@/features/usage/api';
import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Empty, Row, Skeleton, Statistic, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import type { EChartsCoreOption } from 'echarts/core';
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';

/** 用量端点只接受 yyyy-MM-dd（ISO 时间戳会 400）。 */
function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

export default function OverviewPage() {
  const navigate = useNavigate();
  const from = useMemo(() => daysAgo(7), []);
  const to = useMemo(() => new Date().toISOString().slice(0, 10), []);

  const usageQuery = useQuery({
    queryKey: ['overview', 'usage', from, to],
    queryFn: () => fetchUsage({ from, to }),
    retry: false,
  });

  const keysQuery = useQuery({
    queryKey: ['overview', 'keys'],
    queryFn: () => fetchKeys('ok', { limit: 200, offset: 0 }),
    retry: false,
  });

  const modelsQuery = useQuery({
    queryKey: ['overview', 'models'],
    queryFn: () => fetchModels('ok'),
    retry: false,
  });

  const mcpQuery = useQuery({
    queryKey: ['overview', 'mcp'],
    queryFn: () => fetchMcpServers('ok'),
    retry: false,
  });

  const mcpStatsQuery = useQuery({
    queryKey: ['overview', 'mcp-stats'],
    queryFn: () => fetchMcpRuntimeStats('ok'),
    retry: false,
  });

  const logsQuery = useQuery({
    queryKey: ['overview', 'logs'],
    queryFn: () => fetchLogs({ limit: 8, offset: 0 }),
    retry: false,
  });

  const keys = keysQuery.data?.items ?? [];
  const activeKeys = keys.filter((key) => keyStatus(key) === 'ACTIVE').length;
  const mcpServers = mcpQuery.data ?? [];
  const healthyMcp = mcpServers.filter((server) => server.status === 'UP').length;
  const recentLogs = logsQuery.data?.items ?? [];

  const refreshAll = () => {
    void usageQuery.refetch();
    void keysQuery.refetch();
    void modelsQuery.refetch();
    void mcpQuery.refetch();
    void mcpStatsQuery.refetch();
    void logsQuery.refetch();
  };

  const trendOption = useMemo<EChartsCoreOption>(() => {
    const trend = usageQuery.data?.trend ?? [];
    return {
      grid: { left: 56, right: 56, top: 36, bottom: 32 },
      tooltip: { trigger: 'axis' },
      legend: { data: ['请求数', '成本'], top: 0 },
      xAxis: { type: 'category', data: trend.map((point) => point.date) },
      yAxis: [
        { type: 'value', name: '请求' },
        { type: 'value', name: '成本' },
      ],
      series: [
        {
          name: '请求数',
          type: 'bar',
          data: trend.map((point) => point.requests),
          itemStyle: { color: '#2457e6' },
        },
        {
          name: '成本',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          data: trend.map((point) => point.cost),
          itemStyle: { color: '#d97706' },
        },
      ],
    };
  }, [usageQuery.data]);

  const logColumns: TableProps<(typeof recentLogs)[number]>['columns'] = [
    {
      title: '时间',
      dataIndex: 'created_at',
      width: 150,
      render: (value: string) => <span className="num">{formatDateTime(value)}</span>,
    },
    {
      title: '模型',
      dataIndex: 'model_alias',
      render: (value?: string | null) => <span className="mono">{value ?? '—'}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 76,
      render: (value: number) => {
        const tone = logStatusTone(value);
        return (
          <Tag color={tone === 'success' ? 'success' : tone === 'warning' ? 'warning' : 'error'}>
            {value}
          </Tag>
        );
      },
    },
    {
      title: '延迟',
      dataIndex: 'latency_ms',
      width: 90,
      align: 'right',
      render: (value: number) => <span className="num">{formatLatency(value)}</span>,
    },
  ];

  const errorSources = [
    usageQuery.isError ? '用量' : null,
    keysQuery.isError ? 'Key' : null,
    modelsQuery.isError ? '模型' : null,
    mcpQuery.isError ? 'MCP' : null,
    logsQuery.isError ? '调用日志' : null,
  ].filter(Boolean);

  const loading = usageQuery.isPending || keysQuery.isPending || modelsQuery.isPending;

  return (
    <>
      <PageHeader
        title="概览"
        section="概览"
        description="近 7 天的请求与成本趋势、Key 与模型规模、MCP 健康与最近调用；全部来自真实 console 接口。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={refreshAll}>
            刷新
          </Button>
        }
      />

      {errorSources.length > 0 ? (
        <div className="console-error-panel" role="alert" style={{ marginBottom: 16 }}>
          <div className="console-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: '#dc2626' }}
              aria-hidden="true"
            />
            <strong>部分数据加载失败</strong>
          </div>
          <p className="console-error-desc">
            未拿到：{errorSources.join('、')}。其余区块仍可查看。
          </p>
          <Button size="small" onClick={refreshAll}>
            全部重试
          </Button>
        </div>
      ) : null}

      {loading ? (
        <div className="console-surface console-skeleton">
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      ) : (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} lg={6}>
              <Card size="small">
                <Statistic
                  title="近 7 天请求"
                  value={usageQuery.data?.totals.requests ?? 0}
                  formatter={(value) => formatNumber(Number(value))}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card size="small">
                <Statistic
                  title="近 7 天成本"
                  value={usageQuery.data?.totals.cost ?? 0}
                  precision={4}
                  prefix="¥"
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card size="small">
                <Statistic
                  title="活跃 Key"
                  value={activeKeys}
                  suffix={`/ ${keys.length}`}
                  loading={keysQuery.isPending}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card size="small">
                <Statistic
                  title="MCP 健康"
                  value={healthyMcp}
                  suffix={`/ ${mcpServers.length}`}
                  loading={mcpQuery.isPending}
                />
              </Card>
            </Col>
          </Row>

          <div className="console-surface" style={{ padding: 16, marginTop: 16 }}>
            {usageQuery.data && usageQuery.data.trend.length > 0 ? (
              <Chart option={trendOption} ariaLabel="近 7 天请求数与成本趋势" height={260} />
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="近 7 天没有用量记录；发起一次调用后这里会出现趋势"
              />
            )}
          </div>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} lg={14}>
              <Card
                size="small"
                title="最近调用"
                extra={
                  <Button size="small" type="link" onClick={() => navigate('/logs')}>
                    查看全部
                  </Button>
                }
              >
                {recentLogs.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有调用日志" />
                ) : (
                  <Table
                    rowKey="request_id"
                    size="small"
                    columns={logColumns}
                    dataSource={recentLogs}
                    pagination={false}
                    scroll={{ x: 480 }}
                  />
                )}
              </Card>
            </Col>
            <Col xs={24} lg={10}>
              <Card
                size="small"
                title="MCP 运行状态"
                extra={
                  <Button size="small" type="link" onClick={() => navigate('/mcp-servers')}>
                    管理
                  </Button>
                }
              >
                <div className="console-stat-strip" style={{ marginBottom: 12 }}>
                  <span className="console-stat-item">
                    活跃 stdio{' '}
                    <b className="console-stat-value">
                      {mcpStatsQuery.data?.activeStdioProcesses ?? '—'}
                    </b>
                  </span>
                  <span className="console-stat-item">
                    探活中{' '}
                    <b className="console-stat-value">
                      {mcpStatsQuery.data?.inFlightProbeHttp ?? '—'}
                    </b>
                  </span>
                  <span className="console-stat-item">
                    工具调用中{' '}
                    <b className="console-stat-value">
                      {mcpStatsQuery.data?.inFlightToolCalls ?? '—'}
                    </b>
                  </span>
                </div>
                {mcpServers.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有注册 MCP Server" />
                ) : (
                  <div>
                    {mcpServers.slice(0, 6).map((server) => (
                      <div
                        key={server.serverId}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '6px 0',
                          borderBottom: '1px solid var(--color-border-soft)',
                        }}
                      >
                        <div>
                          <div>{server.name}</div>
                          <div className="mono muted">{server.serverId}</div>
                        </div>
                        <span className="status-inline">
                          <span
                            className="status-dot"
                            style={{ backgroundColor: MCP_STATUS_DOT_COLOR[server.status] }}
                            aria-hidden="true"
                          />
                          {MCP_STATUS_LABEL[server.status]}
                          <span className="muted num" style={{ marginLeft: 8 }}>
                            {formatMcpDateTime(server.lastHealthAt)}
                          </span>
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            </Col>
          </Row>

          <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
            成本合计 {formatCost(usageQuery.data?.totals.cost)} · 模型别名{' '}
            {modelsQuery.data?.length ?? '—'} 个 · 更新于 {formatDateTime(to)}
          </p>
        </>
      )}
    </>
  );
}
