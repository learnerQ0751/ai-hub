import { PageHeader } from '@/components/PageHeader';
import {
  fetchMcpRuntimeStats,
  fetchMcpServers,
  registerMcpServer,
  removeMcpServer,
  reprobeMcpServer,
} from '@/features/mcp/mock';
import type { McpMockMode, RegisterMcpServerInput } from '@/features/mcp/mock';
import {
  filterMcpServers,
  formatDateTime,
  MCP_STATUS_DOT_COLOR,
  MCP_STATUS_LABEL,
  MCP_TRANSPORT_LABEL,
} from '@/features/mcp/types';
import type { McpServer, McpServerStatus, McpTransport } from '@/features/mcp/types';
import { useIsNarrow } from '@/hooks/useIsNarrow';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Popconfirm,
  Select,
  Skeleton,
  Space,
  Table,
  Tooltip,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';

const statusOptions: { value: McpServerStatus; label: string }[] = [
  { value: 'UP', label: '健康' },
  { value: 'DOWN', label: '异常' },
  { value: 'UNKNOWN', label: '未知' },
];

const transportOptions: { value: McpTransport; label: string }[] = [
  { value: 'http', label: 'HTTP' },
  { value: 'sse', label: 'SSE' },
  { value: 'stdio', label: 'stdio' },
];

const mockModeOptions: { value: McpMockMode; label: string }[] = [
  { value: 'ok', label: '正常' },
  { value: 'loading', label: '加载中' },
  { value: 'empty', label: '空数据' },
  { value: 'error', label: '加载失败' },
];

function StatusText({ status }: { status: McpServerStatus }) {
  return (
    <span className="status-inline">
      <span
        className="status-dot"
        style={{ backgroundColor: MCP_STATUS_DOT_COLOR[status] }}
        aria-hidden="true"
      />
      {MCP_STATUS_LABEL[status]}
    </span>
  );
}

function connectionOf(server: McpServer): string {
  return server.endpoint ?? `${server.command ?? ''} ${server.args.join(' ')}`.trim();
}

function ListSkeleton() {
  return (
    <div className="mcp-surface mcp-skeleton" aria-busy="true" aria-label="正在加载 MCP Server">
      <div className="mcp-skeleton-label">正在加载 MCP Server…</div>
      <Skeleton active title={false} paragraph={{ rows: 8, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

interface McpServersPageProps {
  initialMockMode?: McpMockMode;
  hideMockControl?: boolean;
}

export default function McpServersPage({
  initialMockMode = 'ok',
  hideMockControl = false,
}: McpServersPageProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const isNarrow = useIsNarrow();

  const [search, setSearch] = useState('');
  const [tenant, setTenant] = useState<string | undefined>();
  const [status, setStatus] = useState<McpServerStatus | undefined>();
  const [transport, setTransport] = useState<McpTransport | undefined>();
  const [mockMode, setMockMode] = useState<McpMockMode>(initialMockMode);
  const [detail, setDetail] = useState<McpServer | null>(null);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [form] = Form.useForm<RegisterMcpServerInput>();

  const serversQuery = useQuery({
    queryKey: ['mcp', 'servers', mockMode],
    queryFn: () => fetchMcpServers(mockMode),
    retry: false,
  });

  const statsQuery = useQuery({
    queryKey: ['mcp', 'runtime', mockMode],
    queryFn: () => fetchMcpRuntimeStats(mockMode),
    retry: false,
  });

  const reprobeMutation = useMutation({
    mutationFn: reprobeMcpServer,
    onSuccess: (server) => {
      message.success(`已重新探活：${server.name}`);
      setDetail(server);
      void queryClient.invalidateQueries({ queryKey: ['mcp', 'servers'] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const registerMutation = useMutation({
    mutationFn: registerMcpServer,
    onSuccess: (server) => {
      message.success('已注册 MCP Server（mock）');
      setRegisterOpen(false);
      form.resetFields();
      setDetail(server);
      void queryClient.invalidateQueries({ queryKey: ['mcp', 'servers'] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const removeMutation = useMutation({
    mutationFn: removeMcpServer,
    onSuccess: (_data, serverId) => {
      message.success('已注销 MCP Server（mock）');
      if (detail?.serverId === serverId) {
        setDetail(null);
      }
      void queryClient.invalidateQueries({ queryKey: ['mcp', 'servers'] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const servers = useMemo(() => serversQuery.data ?? [], [serversQuery.data]);
  const stats = statsQuery.data;
  const statValue = (value?: number) => (statsQuery.isSuccess ? (value ?? 0) : '—');

  const tenants = useMemo(
    () => Array.from(new Set(servers.map((server) => server.tenantId))).sort(),
    [servers],
  );

  const filteredServers = useMemo(
    () => filterMcpServers(servers, { keyword: search, tenantId: tenant, status, transport }),
    [servers, search, tenant, status, transport],
  );

  const hasFilters = Boolean(search.trim() || tenant || status || transport);
  const transportValue = Form.useWatch('transport', form) ?? 'http';
  const needsEndpoint = transportValue === 'http' || transportValue === 'sse';
  const needsCommand = transportValue === 'stdio';

  const clearFilters = () => {
    setSearch('');
    setTenant(undefined);
    setStatus(undefined);
    setTransport(undefined);
  };

  const isReprobing = (serverId: string) =>
    reprobeMutation.isPending && reprobeMutation.variables === serverId;

  const renderActions = (record: McpServer) => (
    <Space size={4} wrap>
      <Button size="small" onClick={() => setDetail(record)}>
        详情
      </Button>
      <Button
        size="small"
        loading={isReprobing(record.serverId)}
        onClick={() => reprobeMutation.mutate(record.serverId)}
      >
        重探活
      </Button>
      <Popconfirm
        title="注销这个 MCP Server？"
        description="注销后工具目录会移除它（mock）。"
        okText="注销"
        cancelText="取消"
        onConfirm={() => removeMutation.mutate(record.serverId)}
      >
        <Button size="small" danger>
          注销
        </Button>
      </Popconfirm>
    </Space>
  );

  const columns: TableProps<McpServer>['columns'] = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      fixed: 'left',
      width: 188,
      render: (_value, record) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.name}</div>
          <div className="mono muted">{record.serverId}</div>
        </div>
      ),
    },
    {
      title: '连接',
      key: 'connection',
      width: 260,
      render: (_value, record) => {
        const connection = connectionOf(record);
        return (
          <Tooltip title={connection}>
            <div>
              <div className="mono text-ellipsis">{connection || '—'}</div>
              <div className="muted" style={{ fontSize: 12 }}>
                {MCP_TRANSPORT_LABEL[record.transport]}
              </div>
            </div>
          </Tooltip>
        );
      },
    },
    {
      title: '租户',
      dataIndex: 'tenantId',
      width: 104,
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 92,
      render: (value: McpServerStatus, record) => (
        <Tooltip title={record.lastError}>
          <span>
            <StatusText status={value} />
          </span>
        </Tooltip>
      ),
    },
    {
      title: '最近探活',
      dataIndex: 'lastHealthAt',
      width: 156,
      render: (value?: string) => <span className="num muted">{formatDateTime(value)}</span>,
    },
    {
      title: '工具数',
      dataIndex: 'toolCount',
      width: 72,
      align: 'right',
      render: (value: number) => <span className="num">{value}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 186,
      fixed: 'right',
      render: (_value, record) => renderActions(record),
    },
  ];

  return (
    <>
      <PageHeader
        title="MCP Servers"
        section="能力目录"
        description="注册、探活与工具发现。数据为 mock；接口路径集中在 api 目录，契约冻结后整体替换。"
        extra={
          <Space wrap>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                void serversQuery.refetch();
                void statsQuery.refetch();
              }}
            >
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setRegisterOpen(true)}>
              注册 Server
            </Button>
          </Space>
        }
      />

      <div className="mcp-runtime">
        <span className="mcp-runtime-item">
          活跃 stdio 进程{' '}
          <b className="mcp-runtime-value">{statValue(stats?.activeStdioProcesses)}</b>
        </span>
        <span className="mcp-runtime-item">
          进行中探活 <b className="mcp-runtime-value">{statValue(stats?.inFlightProbeHttp)}</b>
        </span>
        <span className="mcp-runtime-item">
          进行中工具调用 <b className="mcp-runtime-value">{statValue(stats?.inFlightToolCalls)}</b>
        </span>
        <span className="mcp-runtime-note">运行时计数 · mock</span>
      </div>

      <div className="mcp-toolbar">
        <Input
          allowClear
          className="mcp-search"
          prefix={<SearchOutlined />}
          placeholder="搜索名称 / Server ID / 端点"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Select
          allowClear
          className="mcp-select"
          popupMatchSelectWidth={false}
          placeholder="租户"
          value={tenant}
          onChange={setTenant}
          options={tenants.map((value) => ({ value, label: value }))}
        />
        <Select
          allowClear
          className="mcp-select"
          popupMatchSelectWidth={false}
          placeholder="状态"
          value={status}
          onChange={setStatus}
          options={statusOptions}
        />
        <Select
          allowClear
          className="mcp-select"
          popupMatchSelectWidth={false}
          placeholder="传输"
          value={transport}
          onChange={setTransport}
          options={transportOptions}
        />
        {hideMockControl ? null : (
          <>
            <div className="mcp-toolbar-spacer" />
            <span className="mcp-mock-label">演示状态</span>
            <Select
              className="mcp-mock-select"
              value={mockMode}
              onChange={setMockMode}
              options={mockModeOptions}
            />
          </>
        )}
      </div>

      {serversQuery.isError ? (
        <div className="mcp-error-panel" role="alert">
          <div className="mcp-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: MCP_STATUS_DOT_COLOR.DOWN }}
              aria-hidden="true"
            />
            <strong>加载 MCP Server 失败</strong>
          </div>
          <p className="mcp-error-desc">
            {serversQuery.error instanceof Error ? serversQuery.error.message : '未知错误'}
          </p>
          <Button size="small" onClick={() => void serversQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : serversQuery.isLoading ? (
        <ListSkeleton />
      ) : filteredServers.length === 0 ? (
        <div className="mcp-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={hasFilters ? '没有匹配的 MCP Server' : '还没有注册 MCP Server'}
          >
            {hasFilters ? (
              <Button onClick={clearFilters}>清除筛选</Button>
            ) : (
              <Button type="primary" onClick={() => setRegisterOpen(true)}>
                注册第一个 Server
              </Button>
            )}
          </Empty>
        </div>
      ) : isNarrow ? (
        <div className="mcp-cards">
          {filteredServers.map((server) => (
            <article className="mcp-card" key={server.serverId}>
              <div className="mcp-card-head">
                <div>
                  <div className="mcp-card-name">{server.name}</div>
                  <div className="mono muted mcp-card-id">{server.serverId}</div>
                </div>
                <StatusText status={server.status} />
              </div>
              <div className="mcp-card-conn mono" title={connectionOf(server)}>
                {connectionOf(server) || '—'}
              </div>
              <dl className="mcp-card-meta">
                <div>
                  <dt>传输</dt>
                  <dd>{MCP_TRANSPORT_LABEL[server.transport]}</dd>
                </div>
                <div>
                  <dt>租户</dt>
                  <dd className="mono">{server.tenantId}</dd>
                </div>
                <div>
                  <dt>最近探活</dt>
                  <dd className="num">{formatDateTime(server.lastHealthAt)}</dd>
                </div>
                <div>
                  <dt>工具数</dt>
                  <dd className="num">{server.toolCount}</dd>
                </div>
              </dl>
              {server.lastError ? <div className="mcp-card-error">{server.lastError}</div> : null}
              <div className="mcp-card-actions">{renderActions(server)}</div>
            </article>
          ))}
          <div className="mcp-cards-count">共 {filteredServers.length} 个 Server</div>
        </div>
      ) : (
        <div className="mcp-surface mcp-table">
          <Table<McpServer>
            rowKey="serverId"
            size="middle"
            columns={columns}
            dataSource={filteredServers}
            scroll={{ x: 1040 }}
            pagination={{
              pageSize: 8,
              showSizeChanger: false,
              showTotal: (total) => `共 ${total} 个 Server`,
            }}
          />
        </div>
      )}

      <Drawer
        title={detail?.name ?? 'MCP Server'}
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        width={isNarrow ? '100%' : 520}
        extra={
          detail ? (
            <Button
              size="small"
              loading={isReprobing(detail.serverId)}
              onClick={() => reprobeMutation.mutate(detail.serverId)}
            >
              重新探活
            </Button>
          ) : null
        }
      >
        {detail ? (
          <>
            <Descriptions
              size="small"
              column={1}
              bordered
              items={[
                {
                  key: 'serverId',
                  label: 'Server ID',
                  children: <span className="mono">{detail.serverId}</span>,
                },
                { key: 'name', label: '名称', children: detail.name },
                {
                  key: 'transport',
                  label: '传输',
                  children: MCP_TRANSPORT_LABEL[detail.transport],
                },
                {
                  key: 'connection',
                  label: detail.transport === 'stdio' ? '命令' : '端点',
                  children: <span className="mono">{connectionOf(detail) || '—'}</span>,
                },
                {
                  key: 'tenant',
                  label: '租户',
                  children: <span className="mono">{detail.tenantId}</span>,
                },
                {
                  key: 'status',
                  label: '状态',
                  children: <StatusText status={detail.status} />,
                },
                {
                  key: 'lastHealthAt',
                  label: '最近探活',
                  children: <span className="num">{formatDateTime(detail.lastHealthAt)}</span>,
                },
                {
                  key: 'createdAt',
                  label: '创建时间',
                  children: <span className="num">{formatDateTime(detail.createdAt)}</span>,
                },
                {
                  key: 'description',
                  label: '描述',
                  children: detail.description ?? '—',
                },
              ]}
            />

            {detail.lastError ? (
              <div className="mcp-detail-error">
                <div className="mcp-error-head">
                  <span
                    className="status-dot"
                    style={{ backgroundColor: MCP_STATUS_DOT_COLOR.DOWN }}
                    aria-hidden="true"
                  />
                  <strong>最近错误</strong>
                </div>
                <div className="mono">{detail.lastError}</div>
              </div>
            ) : null}

            <Typography.Title level={5} style={{ marginTop: 24 }}>
              工具与运行时
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
              已发现 {detail.toolCount} 个工具。完整工具目录在「MCP Servers → 工具目录」中查看。
            </Typography.Paragraph>
            <div className="muted" style={{ fontSize: 12 }}>
              运行时：活跃 stdio 进程{' '}
              <span className="num">{statValue(stats?.activeStdioProcesses)}</span> · 进行中探活{' '}
              <span className="num">{statValue(stats?.inFlightProbeHttp)}</span> · 进行中工具调用{' '}
              <span className="num">{statValue(stats?.inFlightToolCalls)}</span>
            </div>
          </>
        ) : null}
      </Drawer>

      <Drawer
        title="注册 MCP Server"
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        width={isNarrow ? '100%' : 480}
      >
        <Form<RegisterMcpServerInput>
          form={form}
          layout="vertical"
          initialValues={{ transport: 'http', args: [], tenantId: 'platform' }}
          onFinish={(values) => registerMutation.mutate({ ...values, args: values.args ?? [] })}
        >
          <Form.Item
            label="名称"
            name="name"
            rules={[{ required: true, message: '请输入 Server 名称' }]}
          >
            <Input placeholder="例如 knowledge-base" />
          </Form.Item>

          <Form.Item label="传输" name="transport" rules={[{ required: true }]}>
            <Select options={transportOptions} />
          </Form.Item>

          <Form.Item
            label="端点"
            name="endpoint"
            hidden={!needsEndpoint}
            rules={needsEndpoint ? [{ required: true, message: '请输入端点 URL' }] : []}
          >
            <Input placeholder="https://mcp.example.com/mcp" />
          </Form.Item>

          <Form.Item
            label="命令"
            name="command"
            hidden={!needsCommand}
            rules={needsCommand ? [{ required: true, message: '请输入启动命令' }] : []}
          >
            <Input placeholder="npx" />
          </Form.Item>

          <Form.Item label="参数" name="args">
            <Select mode="tags" tokenSeparators={[' ']} placeholder="输入参数后回车" />
          </Form.Item>

          <Form.Item
            label="租户"
            name="tenantId"
            rules={[{ required: true, message: '请输入租户 ID' }]}
          >
            <Input placeholder="platform" />
          </Form.Item>

          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} placeholder="这个 Server 提供什么工具" />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={registerMutation.isPending}>
                注册
              </Button>
              <Button onClick={() => setRegisterOpen(false)}>取消</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
}
