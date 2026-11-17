import { PageHeader } from '@/components/PageHeader';
import { fetchKeyAliasOptions, fetchLogs } from '@/features/logs/api';
import {
  formatCost,
  formatLatency,
  formatTokens,
  LOG_STATUS_OPTIONS,
  logStatusTone,
} from '@/features/logs/types';
import type { AdminLog } from '@/features/logs/types';
import { formatDateTime } from '@/features/shared/format';
import type { MockMode } from '@/features/shared/mock';
import { useIsNarrow } from '@/hooks/useIsNarrow';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Select,
  Skeleton,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';

const PAGE_SIZES = [
  { value: 10, label: '10 条/页' },
  { value: 20, label: '20 条/页' },
  { value: 50, label: '50 条/页' },
];

function StatusTag({ status }: { status: number }) {
  const tone = logStatusTone(status);
  return (
    <Tag
      color={tone === 'success' ? 'success' : tone === 'warning' ? 'warning' : 'error'}
      style={{ marginInlineEnd: 0 }}
    >
      {status}
    </Tag>
  );
}

function ListSkeleton() {
  return (
    <div
      className="console-surface console-skeleton"
      aria-busy="true"
      aria-label="正在加载调用日志"
    >
      <div className="console-skeleton-label">正在加载调用日志…</div>
      <Skeleton active title={false} paragraph={{ rows: 8, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

interface LogsPageProps {
  initialDemoMode?: MockMode;
}

export default function LogsPage({ initialDemoMode }: LogsPageProps) {
  const isNarrow = useIsNarrow();

  const [range, setRange] = useState<{ from?: string; to?: string }>({});
  const [modelInput, setModelInput] = useState('');
  const [model, setModel] = useState<string | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [keyId, setKeyId] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [pickerKey, setPickerKey] = useState(0);
  const [detail, setDetail] = useState<AdminLog | null>(null);

  const logsQuery = useQuery({
    queryKey: ['logs', 'list', range.from, range.to, model, status, keyId, page, pageSize],
    queryFn: () =>
      fetchLogs({
        model,
        status,
        key_id: keyId,
        from: range.from,
        to: range.to,
        limit: pageSize,
        offset: (page - 1) * pageSize,
      }),
    retry: false,
    placeholderData: keepPreviousData,
  });

  const keysQuery = useQuery({
    queryKey: ['keys', 'alias-options'],
    queryFn: fetchKeyAliasOptions,
    retry: false,
    staleTime: 60_000,
  });

  const keyOptions = useMemo(() => keysQuery.data ?? [], [keysQuery.data]);
  const aliasByHash = useMemo(() => {
    const map = new Map<string, string>();
    for (const option of keyOptions) {
      const alias = option.label.split('（')[0];
      if (alias && alias !== option.value) {
        map.set(option.value, alias);
      }
    }
    return map;
  }, [keyOptions]);

  const items = useMemo(() => logsQuery.data?.items ?? [], [logsQuery.data]);
  const hasMore = logsQuery.data?.has_more ?? false;
  const visibleItems = initialDemoMode === 'empty' ? [] : items;

  const hasFilters = Boolean(range.from || range.to || model || status !== undefined || keyId);

  const resetFilters = () => {
    setRange({});
    setModelInput('');
    setModel(undefined);
    setStatus(undefined);
    setKeyId(undefined);
    setPage(1);
    setPickerKey((value) => value + 1);
  };

  const columns: TableProps<AdminLog>['columns'] = [
    {
      title: '时间',
      dataIndex: 'created_at',
      width: 160,
      render: (value: string) => <span className="num">{formatDateTime(value)}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 88,
      render: (value: number) => <StatusTag status={value} />,
    },
    {
      title: '模型',
      key: 'model',
      width: 180,
      render: (_value, record) => (
        <div>
          <div>{record.model_alias ?? '—'}</div>
          <div className="mono muted">{record.deployment_model ?? '—'}</div>
        </div>
      ),
    },
    {
      title: 'Key',
      dataIndex: 'key_id',
      width: 170,
      render: (value?: string | null) => (
        <div>
          <div>{value ? (aliasByHash.get(value) ?? '—') : '—'}</div>
          <div className="mono muted">{value ?? '—'}</div>
        </div>
      ),
    },
    {
      title: '租户 / 项目',
      key: 'tenant',
      width: 150,
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
      title: 'Tokens（入 / 出 / 总）',
      key: 'tokens',
      width: 160,
      align: 'right',
      render: (_value, record) => <span className="num">{formatTokens(record)}</span>,
    },
    {
      title: '费用',
      dataIndex: 'cost',
      width: 96,
      align: 'right',
      render: (value?: number | null) => <span className="num">{formatCost(value)}</span>,
    },
    {
      title: '延迟',
      dataIndex: 'latency_ms',
      width: 96,
      align: 'right',
      render: (value: number) => <span className="num">{formatLatency(value)}</span>,
    },
    {
      title: '错误',
      dataIndex: 'error_type',
      width: 150,
      render: (value?: string | null) =>
        value ? <span className="mono">{value}</span> : <span className="muted">—</span>,
    },
    {
      title: '请求 / 追踪',
      key: 'trace',
      width: 200,
      render: (_value, record) => (
        <div>
          <div className="mono">{record.request_id}</div>
          <div className="mono muted">{record.trace_id ?? '—'}</div>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 88,
      fixed: 'right',
      render: (_value, record) => (
        <Button size="small" onClick={() => setDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const showError =
    initialDemoMode === 'error' || (initialDemoMode === undefined && logsQuery.isError);
  const showLoading =
    initialDemoMode === 'loading' || (initialDemoMode === undefined && logsQuery.isPending);

  return (
    <>
      <PageHeader
        title="调用日志"
        section="可观测"
        description="网关调用明细与错误定位；数据来自 console /v1/admin/logs，筛选与分页全部由服务端执行（契约 §4.7）。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void logsQuery.refetch()}>
            刷新
          </Button>
        }
      />

      <div className="console-stat-strip">
        <span className="console-stat-item">
          第 <b className="console-stat-value">{showError || showLoading ? '—' : page}</b> 页
        </span>
        <span className="console-stat-item">
          本页{' '}
          <b className="console-stat-value">
            {showError || showLoading ? '—' : visibleItems.length}
          </b>{' '}
          条
        </span>
        <span className="console-stat-item">
          {showError || showLoading ? '—' : hasMore ? '还有下一页' : '已到末页'}
        </span>
        <span className="console-stat-note">真实接口 · 服务端分页</span>
      </div>

      <div className="console-toolbar">
        <DatePicker.RangePicker
          key={pickerKey}
          showTime
          allowClear
          className="console-range"
          placeholder={['起始时间', '结束时间']}
          onChange={(dates) => {
            setPage(1);
            setRange({
              from: dates?.[0]?.toISOString(),
              to: dates?.[1]?.toISOString(),
            });
          }}
        />
        <Input.Search
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="模型别名（精确）"
          value={modelInput}
          onChange={(event) => setModelInput(event.target.value)}
          onSearch={(value) => {
            setPage(1);
            setModel(value.trim() || undefined);
          }}
          enterButton="查询"
        />
        <Select
          allowClear
          className="console-select"
          popupMatchSelectWidth={false}
          placeholder="状态码"
          value={status}
          onChange={(value) => {
            setPage(1);
            setStatus(value);
          }}
          options={LOG_STATUS_OPTIONS}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          className="console-select-wide"
          popupMatchSelectWidth={false}
          placeholder="Key 别名"
          value={keyId}
          onChange={(value) => {
            setPage(1);
            setKeyId(value);
          }}
          options={keyOptions}
          notFoundContent={keysQuery.isError ? '无法加载 Key 列表' : '没有匹配的 Key'}
        />
        {hasFilters ? <Button onClick={resetFilters}>清除筛选</Button> : null}
      </div>

      {showError ? (
        <div className="console-error-panel" role="alert">
          <div className="console-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: '#dc2626' }}
              aria-hidden="true"
            />
            <strong>加载调用日志失败</strong>
          </div>
          <p className="console-error-desc">
            {initialDemoMode === 'error'
              ? '演示状态：请求失败时显示重试入口。'
              : logsQuery.error instanceof Error
                ? logsQuery.error.message
                : '未知错误'}
          </p>
          <Button size="small" onClick={() => void logsQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : showLoading ? (
        <ListSkeleton />
      ) : visibleItems.length === 0 ? (
        <div className="console-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={hasFilters ? '没有匹配的调用日志' : '还没有调用日志'}
          >
            {hasFilters ? (
              <Button onClick={resetFilters}>清除筛选</Button>
            ) : (
              <span className="muted" style={{ fontSize: 12 }}>
                调用日志由网关写入；先发起一次正常调用，这里才会有记录。
              </span>
            )}
          </Empty>
        </div>
      ) : isNarrow ? (
        <div className="console-cards">
          {visibleItems.map((log) => (
            <article className="console-card" key={log.request_id}>
              <div className="console-card-head">
                <div>
                  <div className="console-card-name">{log.model_alias ?? '未知模型'}</div>
                  <div className="mono muted console-card-id">{log.request_id}</div>
                </div>
                <StatusTag status={log.status} />
              </div>
              <dl className="console-card-meta">
                <div>
                  <dt>时间</dt>
                  <dd className="num">{formatDateTime(log.created_at)}</dd>
                </div>
                <div>
                  <dt>Key</dt>
                  <dd className="mono">
                    {log.key_id ? (aliasByHash.get(log.key_id) ?? log.key_id) : '—'}
                  </dd>
                </div>
                <div>
                  <dt>Tokens（入 / 出 / 总）</dt>
                  <dd className="num">{formatTokens(log)}</dd>
                </div>
                <div>
                  <dt>费用 / 延迟</dt>
                  <dd className="num">
                    {formatCost(log.cost)} / {formatLatency(log.latency_ms)}
                  </dd>
                </div>
                <div>
                  <dt>租户 / 项目</dt>
                  <dd className="mono">
                    {log.tenant_id ?? '—'} / {log.project_id ?? '—'}
                  </dd>
                </div>
                <div>
                  <dt>错误</dt>
                  <dd className="mono">{log.error_type ?? '—'}</dd>
                </div>
              </dl>
              <div className="console-card-actions">
                <Button size="small" onClick={() => setDetail(log)}>
                  详情
                </Button>
              </div>
            </article>
          ))}
          <div className="console-cards-count">
            第 {page} 页 · {visibleItems.length} 条 · {hasMore ? '还有下一页' : '末页'}
          </div>
        </div>
      ) : (
        <div className="console-surface console-table">
          <Table<AdminLog>
            rowKey="request_id"
            size="middle"
            columns={columns}
            dataSource={visibleItems}
            scroll={{ x: 1520 }}
            loading={logsQuery.isFetching && !logsQuery.isPending}
            pagination={false}
          />
        </div>
      )}

      {!showError && !showLoading && visibleItems.length > 0 ? (
        <div className="console-pager">
          <span className="console-pager-info">
            第 {page} 页 · 本页 {visibleItems.length} 条 · {hasMore ? '还有下一页' : '已到末页'}
          </span>
          <Select
            size="small"
            value={pageSize}
            onChange={(value) => {
              setPage(1);
              setPageSize(value);
            }}
            options={PAGE_SIZES}
          />
          <Button size="small" disabled={page === 1} onClick={() => setPage((value) => value - 1)}>
            上一页
          </Button>
          <Button size="small" disabled={!hasMore} onClick={() => setPage((value) => value + 1)}>
            下一页
          </Button>
        </div>
      ) : null}

      <Drawer
        title="调用日志详情"
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        width={isNarrow ? '100%' : 560}
      >
        {detail ? (
          <Descriptions
            size="small"
            column={1}
            bordered
            items={[
              {
                key: 'request_id',
                label: '请求 ID',
                children: <span className="mono">{detail.request_id}</span>,
              },
              {
                key: 'trace_id',
                label: '追踪 ID',
                children: <span className="mono">{detail.trace_id ?? '—'}</span>,
              },
              {
                key: 'time',
                label: '时间',
                children: <span className="num">{formatDateTime(detail.created_at)}</span>,
              },
              {
                key: 'status',
                label: '状态码',
                children: <StatusTag status={detail.status} />,
              },
              {
                key: 'key',
                label: 'Key',
                children: (
                  <span>
                    {detail.key_id ? (aliasByHash.get(detail.key_id) ?? '—') : '—'}
                    <span className="mono muted"> {detail.key_id ?? ''}</span>
                  </span>
                ),
              },
              {
                key: 'tenant',
                label: '租户 / 项目',
                children: (
                  <span className="mono">
                    {detail.tenant_id ?? '—'} / {detail.project_id ?? '—'}
                  </span>
                ),
              },
              {
                key: 'model',
                label: '模型',
                children: (
                  <span>
                    {detail.model_alias ?? '—'}
                    <span className="mono muted"> · {detail.deployment_model ?? '—'}</span>
                  </span>
                ),
              },
              {
                key: 'call',
                label: '调用类型 / 目标',
                children: (
                  <span className="mono">
                    {detail.call_type ?? '—'} · {detail.target_name ?? '—'}
                  </span>
                ),
              },
              {
                key: 'tokens',
                label: 'Tokens（入 / 出 / 总）',
                children: <span className="num">{formatTokens(detail)}</span>,
              },
              {
                key: 'cost',
                label: '费用 / 延迟',
                children: (
                  <span className="num">
                    {formatCost(detail.cost)} / {formatLatency(detail.latency_ms)}
                  </span>
                ),
              },
              {
                key: 'error',
                label: '错误类型',
                children: <span className="mono">{detail.error_type ?? '—'}</span>,
              },
            ]}
          />
        ) : null}
      </Drawer>
    </>
  );
}
