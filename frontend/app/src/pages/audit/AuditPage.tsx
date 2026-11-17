import { PageHeader } from '@/components/PageHeader';
import { fetchAuditLogs } from '@/features/audit/api';
import { AUDIT_ACTIONS, auditActionLabel, formatAuditState } from '@/features/audit/types';
import type { AuditLog } from '@/features/audit/types';
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

function ActionTag({ action }: { action: string }) {
  return (
    <span className="status-inline">
      <span className="status-dot" style={{ backgroundColor: '#2457e6' }} aria-hidden="true" />
      {auditActionLabel(action)}
    </span>
  );
}

function ListSkeleton() {
  return (
    <div
      className="console-surface console-skeleton"
      aria-busy="true"
      aria-label="正在加载审计日志"
    >
      <div className="console-skeleton-label">正在加载审计日志…</div>
      <Skeleton active title={false} paragraph={{ rows: 8, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

interface AuditPageProps {
  initialDemoMode?: MockMode;
}

export default function AuditPage({ initialDemoMode }: AuditPageProps) {
  const isNarrow = useIsNarrow();

  const [range, setRange] = useState<{ from?: string; to?: string }>({});
  const [action, setAction] = useState<string | undefined>();
  const [targetInput, setTargetInput] = useState('');
  const [target, setTarget] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [pickerKey, setPickerKey] = useState(0);
  const [detail, setDetail] = useState<AuditLog | null>(null);

  const auditQuery = useQuery({
    queryKey: ['audit', 'list', range.from, range.to, action, target, page, pageSize],
    queryFn: () =>
      fetchAuditLogs({
        action,
        target,
        from: range.from,
        to: range.to,
        limit: pageSize,
        offset: (page - 1) * pageSize,
      }),
    retry: false,
    placeholderData: keepPreviousData,
  });

  const items = useMemo(() => auditQuery.data?.items ?? [], [auditQuery.data]);
  const hasMore = auditQuery.data?.has_more ?? false;
  const visibleItems = initialDemoMode === 'empty' ? [] : items;
  const hasFilters = Boolean(range.from || range.to || action || target);

  const resetFilters = () => {
    setRange({});
    setAction(undefined);
    setTargetInput('');
    setTarget(undefined);
    setPage(1);
    setPickerKey((value) => value + 1);
  };

  const columns: TableProps<AuditLog>['columns'] = [
    {
      title: '时间',
      dataIndex: 'created_at',
      width: 160,
      render: (value: string) => <span className="num">{formatDateTime(value)}</span>,
    },
    {
      title: '动作',
      dataIndex: 'action',
      width: 190,
      render: (value: string) => (
        <div>
          <ActionTag action={value} />
          <div className="mono muted">{value}</div>
        </div>
      ),
    },
    {
      title: '目标',
      dataIndex: 'target',
      width: 200,
      render: (value?: string | null) => <span className="mono">{value ?? '—'}</span>,
    },
    {
      title: '操作者',
      dataIndex: 'actor',
      width: 130,
      render: (value?: string | null) => value ?? <span className="muted">—</span>,
    },
    {
      title: '租户',
      dataIndex: 'tenant_id',
      width: 130,
      render: (value?: string | null) => <span className="mono">{value ?? '—'}</span>,
    },
    {
      title: '追踪 ID',
      dataIndex: 'trace_id',
      width: 200,
      render: (value?: string | null) => <span className="mono">{value ?? '—'}</span>,
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
    initialDemoMode === 'error' || (initialDemoMode === undefined && auditQuery.isError);
  const showLoading =
    initialDemoMode === 'loading' || (initialDemoMode === undefined && auditQuery.isPending);

  return (
    <>
      <PageHeader
        title="审计日志"
        section="可观测"
        description="管理动作审计与 before/after 差异；数据来自 console /v1/admin/audit-logs，筛选与分页全部由服务端执行（契约 §4.7）。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void auditQuery.refetch()}>
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
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          className="console-select-wide"
          popupMatchSelectWidth={false}
          placeholder="动作（精确）"
          value={action}
          onChange={(value) => {
            setPage(1);
            setAction(value);
          }}
          options={AUDIT_ACTIONS}
        />
        <Input.Search
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="目标（模糊匹配）"
          value={targetInput}
          onChange={(event) => setTargetInput(event.target.value)}
          onSearch={(value) => {
            setPage(1);
            setTarget(value.trim() || undefined);
          }}
          enterButton="查询"
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
            <strong>加载审计日志失败</strong>
          </div>
          <p className="console-error-desc">
            {initialDemoMode === 'error'
              ? '演示状态：请求失败时显示重试入口。'
              : auditQuery.error instanceof Error
                ? auditQuery.error.message
                : '未知错误'}
          </p>
          <Button size="small" onClick={() => void auditQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : showLoading ? (
        <ListSkeleton />
      ) : visibleItems.length === 0 ? (
        <div className="console-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={hasFilters ? '没有匹配的审计记录' : '还没有审计记录'}
          >
            {hasFilters ? (
              <Button onClick={resetFilters}>清除筛选</Button>
            ) : (
              <span className="muted" style={{ fontSize: 12 }}>
                审计记录由控制台写操作产生；先做一次 Key、模型、Prompt 或 MCP 变更。
              </span>
            )}
          </Empty>
        </div>
      ) : isNarrow ? (
        <div className="console-cards">
          {visibleItems.map((record) => (
            <article className="console-card" key={record.id}>
              <div className="console-card-head">
                <div>
                  <div className="console-card-name">{auditActionLabel(record.action)}</div>
                  <div className="mono muted console-card-id">
                    {formatDateTime(record.created_at)}
                  </div>
                </div>
                <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                  {record.action}
                </Tag>
              </div>
              <dl className="console-card-meta">
                <div>
                  <dt>目标</dt>
                  <dd className="mono">{record.target ?? '—'}</dd>
                </div>
                <div>
                  <dt>操作者</dt>
                  <dd>{record.actor ?? '—'}</dd>
                </div>
                <div>
                  <dt>租户</dt>
                  <dd className="mono">{record.tenant_id ?? '—'}</dd>
                </div>
                <div>
                  <dt>追踪 ID</dt>
                  <dd className="mono">{record.trace_id ?? '—'}</dd>
                </div>
              </dl>
              <div className="console-card-actions">
                <Button size="small" onClick={() => setDetail(record)}>
                  详情与差异
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
          <Table<AuditLog>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={visibleItems}
            scroll={{ x: 1100 }}
            loading={auditQuery.isFetching && !auditQuery.isPending}
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
        title="审计详情"
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        width={isNarrow ? '100%' : 600}
      >
        {detail ? (
          <>
            <Descriptions
              size="small"
              column={1}
              bordered
              items={[
                { key: 'id', label: '记录 ID', children: <span className="num">{detail.id}</span> },
                {
                  key: 'time',
                  label: '时间',
                  children: <span className="num">{formatDateTime(detail.created_at)}</span>,
                },
                {
                  key: 'action',
                  label: '动作',
                  children: (
                    <span>
                      <ActionTag action={detail.action} />
                      <span className="mono muted"> {detail.action}</span>
                    </span>
                  ),
                },
                {
                  key: 'target',
                  label: '目标',
                  children: <span className="mono">{detail.target ?? '—'}</span>,
                },
                { key: 'actor', label: '操作者', children: detail.actor ?? '—' },
                {
                  key: 'tenant',
                  label: '租户',
                  children: <span className="mono">{detail.tenant_id ?? '—'}</span>,
                },
                {
                  key: 'trace',
                  label: '追踪 ID',
                  children: <span className="mono">{detail.trace_id ?? '—'}</span>,
                },
              ]}
            />

            <h4 style={{ margin: '20px 0 8px' }}>变更前</h4>
            <pre className="console-code">{formatAuditState(detail.before)}</pre>

            <h4 style={{ margin: '20px 0 8px' }}>变更后</h4>
            <pre className="console-code">{formatAuditState(detail.after)}</pre>
          </>
        ) : null}
      </Drawer>
    </>
  );
}
