import { PageHeader } from '@/components/PageHeader';
import { fetchKeys, setKeyBlocked } from '@/features/keys/api';
import {
  filterKeys,
  formatBudget,
  formatDateTime,
  formatLimits,
  KEY_STATUS_DOT_COLOR,
  KEY_STATUS_LABEL,
  keyName,
  keyStatus,
} from '@/features/keys/types';
import type { AdminKey, KeyStatus } from '@/features/keys/types';
import type { MockMode } from '@/features/shared/mock';
import { MOCK_MODE_OPTIONS } from '@/features/shared/mock';
import { formatNumber } from '@/features/shared/format';
import { useIsNarrow } from '@/hooks/useIsNarrow';
import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Popconfirm,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';

const statusOptions: { value: KeyStatus; label: string }[] = [
  { value: 'ACTIVE', label: '正常' },
  { value: 'BLOCKED', label: '已封禁' },
  { value: 'EXPIRED', label: '已过期' },
];

function StatusText({ status }: { status: KeyStatus }) {
  return (
    <span className="status-inline">
      <span
        className="status-dot"
        style={{ backgroundColor: KEY_STATUS_DOT_COLOR[status] }}
        aria-hidden="true"
      />
      {KEY_STATUS_LABEL[status]}
    </span>
  );
}

function ModelTags({ models }: { models: string[] }) {
  if (models.length === 0) {
    return <span className="muted">—</span>;
  }
  const visible = models.slice(0, 2);
  const rest = models.length - visible.length;
  return (
    <Space size={4} wrap>
      {visible.map((model) => (
        <Tag key={model} style={{ marginInlineEnd: 0 }}>
          {model}
        </Tag>
      ))}
      {rest > 0 ? (
        <Tooltip title={models.join('、')}>
          <Tag style={{ marginInlineEnd: 0 }}>+{rest}</Tag>
        </Tooltip>
      ) : null}
    </Space>
  );
}

function ListSkeleton() {
  return (
    <div
      className="console-surface console-skeleton"
      aria-busy="true"
      aria-label="正在加载虚拟 Key"
    >
      <div className="console-skeleton-label">正在加载虚拟 Key…</div>
      <Skeleton active title={false} paragraph={{ rows: 8, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

interface KeysPageProps {
  initialMockMode?: MockMode;
  hideMockControl?: boolean;
}

export default function KeysPage({
  initialMockMode = 'ok',
  hideMockControl = false,
}: KeysPageProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const isNarrow = useIsNarrow();

  const [search, setSearch] = useState('');
  const [tenant, setTenant] = useState<string | undefined>();
  const [status, setStatus] = useState<KeyStatus | undefined>();
  const [model, setModel] = useState<string | undefined>();
  const [mockMode, setMockMode] = useState<MockMode>(initialMockMode);
  const [detail, setDetail] = useState<AdminKey | null>(null);
  const pageSize = 20;
  const [page, setPage] = useState(1);

  const keysQuery = useQuery({
    queryKey: ['keys', 'list', mockMode, tenant, status, model, page],
    queryFn: () =>
      fetchKeys(mockMode, {
        tenant_id: tenant,
        status: status?.toLowerCase() as 'active' | 'blocked' | 'expired' | undefined,
        model,
        limit: pageSize,
        offset: (page - 1) * pageSize,
      }),
    retry: false,
    placeholderData: (previous) => previous,
  });

  const blockMutation = useMutation({
    mutationFn: ({ keyHash, blocked }: { keyHash: string; blocked: boolean }) =>
      setKeyBlocked(keyHash, blocked),
    onSuccess: (key) => {
      message.success(key.blocked ? `已封禁：${keyName(key)}` : `已解封：${keyName(key)}`);
      if (detail?.key_hash === key.key_hash) {
        setDetail(key);
      }
      void queryClient.invalidateQueries({ queryKey: ['keys', 'list'] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const keys = useMemo(() => keysQuery.data?.items ?? [], [keysQuery.data]);

  const tenants = useMemo(
    () => Array.from(new Set(keys.map((key) => key.tenant_id).filter(Boolean) as string[])).sort(),
    [keys],
  );
  const models = useMemo(
    () => Array.from(new Set(keys.flatMap((key) => key.models))).sort(),
    [keys],
  );

  const filteredKeys = useMemo(
    () => filterKeys(keys, { keyword: search, tenantId: tenant, status, model }),
    [keys, search, tenant, status, model],
  );

  const hasFilters = Boolean(search.trim() || tenant || status || model);

  const clearFilters = () => {
    setSearch('');
    setTenant(undefined);
    setStatus(undefined);
    setModel(undefined);
    setPage(1);
  };

  const isBlocking = (keyHash: string) =>
    blockMutation.isPending && blockMutation.variables?.keyHash === keyHash;

  const renderActions = (record: AdminKey) => (
    <Space size={4} wrap>
      <Button size="small" icon={<EyeOutlined />} onClick={() => setDetail(record)}>
        详情
      </Button>
      <Popconfirm
        title={record.blocked ? '解封这个 Key？' : '封禁这个 Key？'}
        description={
          record.blocked ? '解封后该 Key 可立即恢复调用。' : '封禁后该 Key 的调用会立即失败。'
        }
        okText={record.blocked ? '解封' : '封禁'}
        cancelText="取消"
        okButtonProps={{ danger: !record.blocked }}
        onConfirm={() =>
          blockMutation.mutate({ keyHash: record.key_hash, blocked: !record.blocked })
        }
      >
        <Button size="small" danger={!record.blocked} loading={isBlocking(record.key_hash)}>
          {record.blocked ? '解封' : '封禁'}
        </Button>
      </Popconfirm>
    </Space>
  );

  const columns: TableProps<AdminKey>['columns'] = [
    {
      title: '别名',
      key: 'alias',
      fixed: 'left',
      width: 210,
      render: (_value, record) => (
        <div>
          <div style={{ fontWeight: 500 }}>{keyName(record)}</div>
          <div className="mono muted">{record.key_hash}</div>
        </div>
      ),
    },
    {
      title: '租户 / 项目',
      key: 'tenant',
      width: 148,
      render: (_value, record) => (
        <div>
          <div className="mono">{record.tenant_id ?? '—'}</div>
          <div className="muted" style={{ fontSize: 12 }}>
            {record.project_id ?? '—'}
          </div>
        </div>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 88,
      render: (_value, record) => <StatusText status={keyStatus(record)} />,
    },
    {
      title: '模型',
      key: 'models',
      width: 210,
      render: (_value, record) => <ModelTags models={record.models} />,
    },
    {
      title: '限流',
      key: 'limits',
      width: 176,
      render: (_value, record) => <span className="num muted">{formatLimits(record)}</span>,
    },
    {
      title: '预算',
      key: 'budget',
      width: 160,
      render: (_value, record) => <span className="num">{formatBudget(record)}</span>,
    },
    {
      title: '创建时间',
      dataIndex: 'created_at',
      width: 156,
      render: (value: string) => <span className="num muted">{formatDateTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 148,
      fixed: 'right',
      render: (_value, record) => renderActions(record),
    },
  ];

  const activeCount = keys.filter((key) => keyStatus(key) === 'ACTIVE').length;
  const blockedCount = keys.filter((key) => key.blocked).length;
  const expiredCount = keys.length - activeCount - blockedCount;
  const statValue = (value: number) => (keysQuery.isSuccess ? value : '—');

  return (
    <>
      <PageHeader
        title="虚拟 Key"
        section="访问控制"
        description="租户与项目下的调用凭证、模型白名单、限流与预算；数据来自 console /v1/admin/keys，筛选与分页由服务端执行。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void keysQuery.refetch()}>
            刷新
          </Button>
        }
      />

      <div className="console-stat-strip">
        <span className="console-stat-item">
          共 <b className="console-stat-value">{statValue(keys.length)}</b> 个 Key
        </span>
        <span className="console-stat-item">
          正常 <b className="console-stat-value">{statValue(activeCount)}</b>
        </span>
        <span className="console-stat-item">
          已封禁 <b className="console-stat-value">{statValue(blockedCount)}</b>
        </span>
        <span className="console-stat-item">
          已过期 <b className="console-stat-value">{statValue(expiredCount)}</b>
        </span>
        <span className="console-stat-note">本页口径 · 真实接口</span>
      </div>

      <div className="console-toolbar">
        <Input
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="搜索别名 / Key 哈希 / 租户 / 模型"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Select
          allowClear
          className="console-select"
          popupMatchSelectWidth={false}
          placeholder="租户"
          value={tenant}
          onChange={(value) => {
            setPage(1);
            setTenant(value);
          }}
          options={tenants.map((value) => ({ value, label: value }))}
        />
        <Select
          allowClear
          className="console-select"
          popupMatchSelectWidth={false}
          placeholder="状态"
          value={status}
          onChange={(value) => {
            setPage(1);
            setStatus(value);
          }}
          options={statusOptions}
        />
        <Select
          allowClear
          className="console-select"
          popupMatchSelectWidth={false}
          placeholder="模型"
          value={model}
          onChange={(value) => {
            setPage(1);
            setModel(value);
          }}
          options={models.map((value) => ({ value, label: value }))}
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

      {keysQuery.isError ? (
        <div className="console-error-panel" role="alert">
          <div className="console-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: KEY_STATUS_DOT_COLOR.BLOCKED }}
              aria-hidden="true"
            />
            <strong>加载虚拟 Key 失败</strong>
          </div>
          <p className="console-error-desc">
            {keysQuery.error instanceof Error ? keysQuery.error.message : '未知错误'}
          </p>
          <Button size="small" onClick={() => void keysQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : keysQuery.isLoading ? (
        <ListSkeleton />
      ) : filteredKeys.length === 0 ? (
        <div className="console-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={hasFilters ? '没有匹配的虚拟 Key' : '还没有虚拟 Key'}
          >
            {hasFilters ? <Button onClick={clearFilters}>清除筛选</Button> : null}
          </Empty>
        </div>
      ) : isNarrow ? (
        <div className="console-cards">
          {filteredKeys.map((key) => (
            <article className="console-card" key={key.key_hash}>
              <div className="console-card-head">
                <div>
                  <div className="console-card-name">{keyName(key)}</div>
                  <div className="mono muted console-card-id">{key.key_hash}</div>
                </div>
                <StatusText status={keyStatus(key)} />
              </div>
              <dl className="console-card-meta">
                <div>
                  <dt>租户 / 项目</dt>
                  <dd className="mono">
                    {key.tenant_id ?? '—'} / {key.project_id ?? '—'}
                  </dd>
                </div>
                <div>
                  <dt>模型</dt>
                  <dd>{key.models.join('、') || '—'}</dd>
                </div>
                <div>
                  <dt>限流</dt>
                  <dd className="num">{formatLimits(key)}</dd>
                </div>
                <div>
                  <dt>预算</dt>
                  <dd className="num">{formatBudget(key)}</dd>
                </div>
                <div>
                  <dt>创建时间</dt>
                  <dd className="num">{formatDateTime(key.created_at)}</dd>
                </div>
                <div>
                  <dt>过期时间</dt>
                  <dd className="num">{formatDateTime(key.expires_at)}</dd>
                </div>
              </dl>
              <div className="console-card-actions">{renderActions(key)}</div>
            </article>
          ))}
          <div className="console-cards-count">共 {filteredKeys.length} 个 Key</div>
        </div>
      ) : (
        <div className="console-surface console-table">
          <Table<AdminKey>
            rowKey="key_hash"
            size="middle"
            columns={columns}
            dataSource={filteredKeys}
            scroll={{ x: 1240 }}
            pagination={{
              current: page,
              pageSize,
              showSizeChanger: false,
              total:
                (page - 1) * pageSize + keys.length + (keysQuery.data?.has_more ? pageSize : 0),
              onChange: setPage,
              showTotal: () => (keysQuery.data?.has_more ? '还有下一页' : '已到末页'),
            }}
          />
        </div>
      )}

      <Drawer
        title={detail ? keyName(detail) : '虚拟 Key'}
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        width={isNarrow ? '100%' : 520}
        extra={
          detail ? (
            <Button
              size="small"
              danger={!detail.blocked}
              loading={isBlocking(detail.key_hash)}
              onClick={() =>
                blockMutation.mutate({ keyHash: detail.key_hash, blocked: !detail.blocked })
              }
            >
              {detail.blocked ? '解封' : '封禁'}
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
                  key: 'key_hash',
                  label: 'Key 哈希',
                  children: <span className="mono">{detail.key_hash}</span>,
                },
                { key: 'alias', label: '别名', children: detail.alias ?? '—' },
                {
                  key: 'tenant',
                  label: '租户',
                  children: <span className="mono">{detail.tenant_id ?? '—'}</span>,
                },
                {
                  key: 'project',
                  label: '项目',
                  children: <span className="mono">{detail.project_id ?? '—'}</span>,
                },
                {
                  key: 'status',
                  label: '状态',
                  children: <StatusText status={keyStatus(detail)} />,
                },
                {
                  key: 'models',
                  label: '模型白名单',
                  children: detail.models.length ? (
                    <Space size={4} wrap>
                      {detail.models.map((item) => (
                        <Tag key={item} style={{ marginInlineEnd: 0 }}>
                          {item}
                        </Tag>
                      ))}
                    </Space>
                  ) : (
                    '—'
                  ),
                },
                {
                  key: 'limits',
                  label: '限流',
                  children: (
                    <span className="num">
                      RPM{' '}
                      {detail.rpm_limit === undefined || detail.rpm_limit === null
                        ? '不限'
                        : formatNumber(detail.rpm_limit)}{' '}
                      · TPM{' '}
                      {detail.tpm_limit === undefined || detail.tpm_limit === null
                        ? '不限'
                        : formatNumber(detail.tpm_limit)}
                    </span>
                  ),
                },
                {
                  key: 'budget',
                  label: '预算 / 已用',
                  children: <span className="num">{formatBudget(detail)}</span>,
                },
                {
                  key: 'expires',
                  label: '过期时间',
                  children: <span className="num">{formatDateTime(detail.expires_at)}</span>,
                },
                {
                  key: 'created',
                  label: '创建时间',
                  children: <span className="num">{formatDateTime(detail.created_at)}</span>,
                },
                {
                  key: 'metadata',
                  label: '元数据',
                  children: detail.metadata ? (
                    <div className="mono">
                      {Object.entries(detail.metadata).map(([key, value]) => (
                        <div key={key}>
                          {key}: {String(value)}
                        </div>
                      ))}
                    </div>
                  ) : (
                    '—'
                  ),
                },
              ]}
            />
            <p className="muted" style={{ marginTop: 16, fontSize: 12 }}>
              明文 Key 只在创建时一次性展示，列表与详情均不含明文。
            </p>
          </>
        ) : null}
      </Drawer>
    </>
  );
}
