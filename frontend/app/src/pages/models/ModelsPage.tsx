import { PageHeader } from '@/components/PageHeader';
import { fetchModels } from '@/features/models/api';
import {
  filterModels,
  formatDateTime,
  formatPrice,
  MODEL_STATUS_DOT_COLOR,
  MODEL_STATUS_LABEL,
} from '@/features/models/types';
import type { AdminDeployment, AdminModel, ModelStatus } from '@/features/models/types';
import type { MockMode } from '@/features/shared/mock';
import { MOCK_MODE_OPTIONS } from '@/features/shared/mock';
import { useIsNarrow } from '@/hooks/useIsNarrow';
import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';

const statusOptions: { value: ModelStatus; label: string }[] = [
  { value: 'ACTIVE', label: '启用' },
  { value: 'DISABLED', label: '停用' },
];

function StatusText({ status }: { status: ModelStatus }) {
  return (
    <span className="status-inline">
      <span
        className="status-dot"
        style={{ backgroundColor: MODEL_STATUS_DOT_COLOR[status] }}
        aria-hidden="true"
      />
      {MODEL_STATUS_LABEL[status]}
    </span>
  );
}

function ListSkeleton() {
  return (
    <div
      className="console-surface console-skeleton"
      aria-busy="true"
      aria-label="正在加载模型别名"
    >
      <div className="console-skeleton-label">正在加载模型别名…</div>
      <Skeleton active title={false} paragraph={{ rows: 8, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

interface ModelsPageProps {
  initialMockMode?: MockMode;
  hideMockControl?: boolean;
}

export default function ModelsPage({
  initialMockMode = 'ok',
  hideMockControl = false,
}: ModelsPageProps) {
  const isNarrow = useIsNarrow();

  const [search, setSearch] = useState('');
  const [tenant, setTenant] = useState<string | undefined>();
  const [status, setStatus] = useState<ModelStatus | undefined>();
  const [mockMode, setMockMode] = useState<MockMode>(initialMockMode);
  const [detail, setDetail] = useState<AdminModel | null>(null);

  const modelsQuery = useQuery({
    queryKey: ['models', 'list', mockMode],
    queryFn: () => fetchModels(mockMode),
    retry: false,
  });

  const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);

  const tenants = useMemo(
    () =>
      Array.from(
        new Set(models.map((model) => model.tenant_id).filter(Boolean) as string[]),
      ).sort(),
    [models],
  );

  const filteredModels = useMemo(
    () => filterModels(models, { keyword: search, tenantId: tenant, status }),
    [models, search, tenant, status],
  );

  const hasFilters = Boolean(search.trim() || tenant || status);
  const clearFilters = () => {
    setSearch('');
    setTenant(undefined);
    setStatus(undefined);
  };

  const deploymentColumns: TableProps<AdminDeployment>['columns'] = [
    {
      title: '部署键',
      dataIndex: 'deployment_key',
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '上游模型',
      dataIndex: 'upstream_model',
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '权重 / 优先级',
      key: 'weight',
      align: 'right',
      render: (_value, record) => (
        <span className="num">
          {record.weight} / {record.priority}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: string) => <span className="mono">{value}</span>,
    },
  ];

  const columns: TableProps<AdminModel>['columns'] = [
    {
      title: '别名',
      key: 'alias',
      fixed: 'left',
      width: 290,
      render: (_value, record) => (
        <div>
          <div className="mono" style={{ fontWeight: 500, fontSize: 13 }}>
            {record.alias}
          </div>
          <div className="muted text-ellipsis" style={{ maxWidth: 260, fontSize: 12 }}>
            {record.description ?? '—'}
          </div>
        </div>
      ),
    },
    {
      title: '租户',
      dataIndex: 'tenant_id',
      width: 116,
      render: (value?: string) => <span className="mono">{value ?? '全局'}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 88,
      render: (value: ModelStatus) => <StatusText status={value} />,
    },
    {
      title: '价格（每百万 tokens）',
      key: 'price',
      width: 190,
      render: (_value, record) => <span className="num">{formatPrice(record)}</span>,
    },
    {
      title: 'Fallback',
      key: 'fallbacks',
      width: 200,
      render: (_value, record) =>
        record.fallbacks.length ? (
          <Space size={4} wrap>
            {record.fallbacks.map((alias) => (
              <Tag key={alias} style={{ marginInlineEnd: 0 }}>
                {alias}
              </Tag>
            ))}
          </Space>
        ) : (
          <span className="muted">—</span>
        ),
    },
    {
      title: '部署',
      key: 'deployments',
      width: 88,
      align: 'right',
      render: (_value, record) => <span className="num">{record.deployments.length} 个</span>,
    },
    {
      title: '更新时间',
      dataIndex: 'updated_at',
      width: 156,
      render: (value?: string) => <span className="num muted">{formatDateTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 88,
      fixed: 'right',
      render: (_value, record) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => setDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const activeCount = models.filter((model) => model.status === 'ACTIVE').length;
  const deploymentCount = models.reduce((total, model) => total + model.deployments.length, 0);
  const statValue = (value: number) => (modelsQuery.isSuccess ? value : '—');

  return (
    <>
      <PageHeader
        title="模型与部署"
        section="访问控制"
        description="对外模型别名、fallback 顺序与上游部署实例；数据为 mock，字段按契约 §7.2 预留。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void modelsQuery.refetch()}>
            刷新
          </Button>
        }
      />

      <div className="console-stat-strip">
        <span className="console-stat-item">
          共 <b className="console-stat-value">{statValue(models.length)}</b> 个别名
        </span>
        <span className="console-stat-item">
          启用 <b className="console-stat-value">{statValue(activeCount)}</b>
        </span>
        <span className="console-stat-item">
          停用 <b className="console-stat-value">{statValue(models.length - activeCount)}</b>
        </span>
        <span className="console-stat-item">
          部署实例 <b className="console-stat-value">{statValue(deploymentCount)}</b>
        </span>
        <span className="console-stat-note">列表口径 · mock</span>
      </div>

      <div className="console-toolbar">
        <Input
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="搜索别名 / 租户 / 描述"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Select
          allowClear
          className="console-select"
          popupMatchSelectWidth={false}
          placeholder="租户"
          value={tenant}
          onChange={setTenant}
          options={tenants.map((value) => ({ value, label: value }))}
        />
        <Select
          allowClear
          className="console-select"
          popupMatchSelectWidth={false}
          placeholder="状态"
          value={status}
          onChange={setStatus}
          options={statusOptions}
        />
        {hideMockControl ? null : (
          <>
            <div className="console-toolbar-spacer" />
            <span className="console-mock-label">演示状态</span>
            <Select
              className="console-mock-select"
              value={mockMode}
              onChange={setMockMode}
              options={MOCK_MODE_OPTIONS}
            />
          </>
        )}
      </div>

      {modelsQuery.isError ? (
        <div className="console-error-panel" role="alert">
          <div className="console-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: MODEL_STATUS_DOT_COLOR.DISABLED }}
              aria-hidden="true"
            />
            <strong>加载模型别名失败</strong>
          </div>
          <p className="console-error-desc">
            {modelsQuery.error instanceof Error ? modelsQuery.error.message : '未知错误'}
          </p>
          <Button size="small" onClick={() => void modelsQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : modelsQuery.isLoading ? (
        <ListSkeleton />
      ) : filteredModels.length === 0 ? (
        <div className="console-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={hasFilters ? '没有匹配的模型别名' : '还没有注册模型别名'}
          >
            {hasFilters ? <Button onClick={clearFilters}>清除筛选</Button> : null}
          </Empty>
        </div>
      ) : isNarrow ? (
        <div className="console-cards">
          {filteredModels.map((model) => (
            <article className="console-card" key={model.alias}>
              <div className="console-card-head">
                <div>
                  <div className="console-card-name mono">{model.alias}</div>
                  <div className="muted console-card-id">{model.description ?? '—'}</div>
                </div>
                <StatusText status={model.status} />
              </div>
              <dl className="console-card-meta">
                <div>
                  <dt>租户</dt>
                  <dd className="mono">{model.tenant_id ?? '全局'}</dd>
                </div>
                <div>
                  <dt>价格（每百万 tokens）</dt>
                  <dd className="num">{formatPrice(model)}</dd>
                </div>
                <div>
                  <dt>Fallback</dt>
                  <dd>{model.fallbacks.join('、') || '—'}</dd>
                </div>
                <div>
                  <dt>部署</dt>
                  <dd className="num">{model.deployments.length} 个</dd>
                </div>
                <div>
                  <dt>更新时间</dt>
                  <dd className="num">{formatDateTime(model.updated_at)}</dd>
                </div>
              </dl>
              <div className="console-card-actions">
                <Button size="small" icon={<EyeOutlined />} onClick={() => setDetail(model)}>
                  详情
                </Button>
              </div>
            </article>
          ))}
          <div className="console-cards-count">共 {filteredModels.length} 个模型别名</div>
        </div>
      ) : (
        <div className="console-surface console-table">
          <Table<AdminModel>
            rowKey="alias"
            size="middle"
            columns={columns}
            dataSource={filteredModels}
            scroll={{ x: 1080 }}
            pagination={{
              pageSize: 8,
              showSizeChanger: false,
              showTotal: (total) => `共 ${total} 个模型别名`,
            }}
          />
        </div>
      )}

      <Drawer
        title={detail?.alias ?? '模型别名'}
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        width={isNarrow ? '100%' : 620}
      >
        {detail ? (
          <>
            <Descriptions
              size="small"
              column={1}
              bordered
              items={[
                {
                  key: 'alias',
                  label: '别名',
                  children: <span className="mono">{detail.alias}</span>,
                },
                {
                  key: 'tenant',
                  label: '租户',
                  children: <span className="mono">{detail.tenant_id ?? '全局'}</span>,
                },
                {
                  key: 'status',
                  label: '状态',
                  children: <StatusText status={detail.status} />,
                },
                {
                  key: 'price',
                  label: '价格（每百万 tokens）',
                  children: <span className="num">{formatPrice(detail)}</span>,
                },
                {
                  key: 'fallbacks',
                  label: 'Fallback',
                  children: detail.fallbacks.length ? (
                    <Space size={4} wrap>
                      {detail.fallbacks.map((alias) => (
                        <Tag key={alias} style={{ marginInlineEnd: 0 }}>
                          {alias}
                        </Tag>
                      ))}
                    </Space>
                  ) : (
                    '—'
                  ),
                },
                { key: 'description', label: '描述', children: detail.description ?? '—' },
                {
                  key: 'updated',
                  label: '更新时间',
                  children: <span className="num">{formatDateTime(detail.updated_at)}</span>,
                },
              ]}
            />

            <Typography.Title level={5} style={{ marginTop: 24 }}>
              部署实例
            </Typography.Title>
            <Table<AdminDeployment>
              rowKey="deployment_key"
              size="small"
              columns={deploymentColumns}
              dataSource={detail.deployments}
              pagination={false}
              scroll={{ x: 520 }}
            />
          </>
        ) : null}
      </Drawer>
    </>
  );
}
