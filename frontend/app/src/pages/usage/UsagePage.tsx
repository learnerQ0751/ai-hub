import { Chart } from '@/components/Chart';
import { PageHeader } from '@/components/PageHeader';
import { fetchKeyAliasOptions } from '@/features/logs/api';
import { formatNumber } from '@/features/shared/format';
import { fetchUsage, usageExportUrl } from '@/features/usage/api';
import type { UsageItem, UsageQuery } from '@/features/usage/types';
import { EMPTY_TOTALS } from '@/features/usage/types';
import { useIsNarrow } from '@/hooks/useIsNarrow';
import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, Button, DatePicker, Empty, Input, Select, Skeleton, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import type { EChartsCoreOption } from 'echarts/core';
import { useMemo, useState } from 'react';

function CostText({ value }: { value?: number | null }) {
  return (
    <span className="num">{value === undefined || value === null ? '—' : value.toFixed(4)}</span>
  );
}

function ListSkeleton() {
  return (
    <div className="console-surface console-skeleton" aria-busy="true" aria-label="正在加载用量">
      <div className="console-skeleton-label">正在加载用量与成本…</div>
      <Skeleton active title={false} paragraph={{ rows: 8, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

export default function UsagePage() {
  const isNarrow = useIsNarrow();

  const [range, setRange] = useState<{ from?: string; to?: string }>({});
  const [tenantInput, setTenantInput] = useState('');
  const [tenant, setTenant] = useState<string | undefined>();
  const [projectInput, setProjectInput] = useState('');
  const [project, setProject] = useState<string | undefined>();
  const [modelInput, setModelInput] = useState('');
  const [model, setModel] = useState<string | undefined>();
  const [keyId, setKeyId] = useState<string | undefined>();
  const [pickerKey, setPickerKey] = useState(0);

  const query: UsageQuery = {
    from: range.from,
    to: range.to,
    tenant_id: tenant,
    project_id: project,
    model,
    key_id: keyId,
  };

  const usageQuery = useQuery({
    queryKey: ['usage', 'query', query],
    queryFn: () => fetchUsage(query),
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

  const data = usageQuery.data;
  const totals = data?.totals ?? EMPTY_TOTALS;
  const items = useMemo(() => data?.items ?? [], [data]);
  const hasFilters = Boolean(range.from || range.to || tenant || project || model || keyId);

  const resetFilters = () => {
    setRange({});
    setTenantInput('');
    setTenant(undefined);
    setProjectInput('');
    setProject(undefined);
    setModelInput('');
    setModel(undefined);
    setKeyId(undefined);
    setPickerKey((value) => value + 1);
  };

  const trendOption = useMemo<EChartsCoreOption>(() => {
    const trend = data?.trend ?? [];
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
  }, [data]);

  const columns: TableProps<UsageItem>['columns'] = [
    {
      title: '日期',
      dataIndex: 'date',
      width: 120,
      render: (value: string) => <span className="num">{value}</span>,
    },
    {
      title: '租户 / 项目',
      key: 'tenant',
      width: 170,
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
      title: '模型',
      dataIndex: 'model_alias',
      width: 170,
      render: (value?: string | null) => <span className="mono">{value ?? '—'}</span>,
    },
    {
      title: 'Key',
      dataIndex: 'key_id',
      width: 190,
      render: (value?: string | null) => (
        <div>
          <div>{value ? (aliasByHash.get(value) ?? '—') : '—'}</div>
          <div className="mono muted">{value ?? '—'}</div>
        </div>
      ),
    },
    {
      title: '请求数',
      dataIndex: 'requests',
      width: 96,
      align: 'right',
      render: (value: number) => <span className="num">{formatNumber(value)}</span>,
    },
    {
      title: 'Tokens（入 / 出 / 总）',
      key: 'tokens',
      width: 190,
      align: 'right',
      render: (_value, record) => (
        <span className="num">
          {formatNumber(record.prompt_tokens)} / {formatNumber(record.completion_tokens)} /{' '}
          {formatNumber(record.total_tokens)}
        </span>
      ),
    },
    {
      title: '成本',
      dataIndex: 'cost',
      width: 110,
      align: 'right',
      render: (value: number) => <CostText value={value} />,
    },
  ];

  return (
    <>
      <PageHeader
        title="用量与成本"
        section="可观测"
        description="按日聚合的请求、Token 与成本；数据来自 console /v1/admin/usage，筛选条件与 CSV 导出使用同一套参数。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void usageQuery.refetch()}>
            刷新
          </Button>
        }
      />

      <div className="console-stat-strip">
        <span className="console-stat-item">
          请求 <b className="console-stat-value">{formatNumber(totals.requests)}</b>
        </span>
        <span className="console-stat-item">
          总 Token <b className="console-stat-value">{formatNumber(totals.total_tokens)}</b>
        </span>
        <span className="console-stat-item">
          成本 <b className="console-stat-value">{totals.cost.toFixed(4)}</b>
        </span>
        <span className="console-stat-item">
          入 / 出 Token{' '}
          <b className="console-stat-value">
            {formatNumber(totals.prompt_tokens)} / {formatNumber(totals.completion_tokens)}
          </b>
        </span>
        <span className="console-stat-note">真实接口 · 服务端聚合</span>
      </div>

      <div className="console-toolbar">
        <DatePicker.RangePicker
          key={pickerKey}
          allowClear
          className="console-range"
          placeholder={['起始时间', '结束时间']}
          onChange={(dates) =>
            setRange({
              // 用量端点只接受日期（yyyy-MM-dd），带时间的 ISO 会 400。
              from: dates?.[0]?.format('YYYY-MM-DD'),
              to: dates?.[1]?.format('YYYY-MM-DD'),
            })
          }
        />
        <Input.Search
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="模型别名（精确）"
          value={modelInput}
          onChange={(event) => setModelInput(event.target.value)}
          onSearch={(value) => setModel(value.trim() || undefined)}
          enterButton="查询"
        />
        <Input.Search
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="租户（精确）"
          value={tenantInput}
          onChange={(event) => setTenantInput(event.target.value)}
          onSearch={(value) => setTenant(value.trim() || undefined)}
          enterButton="查询"
        />
        <Input.Search
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="项目（精确）"
          value={projectInput}
          onChange={(event) => setProjectInput(event.target.value)}
          onSearch={(value) => setProject(value.trim() || undefined)}
          enterButton="查询"
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          className="console-select-wide"
          popupMatchSelectWidth={false}
          placeholder="Key 别名"
          value={keyId}
          onChange={setKeyId}
          options={keyOptions}
          notFoundContent={keysQuery.isError ? '无法加载 Key 列表' : '没有匹配的 Key'}
        />
        {hasFilters ? <Button onClick={resetFilters}>清除筛选</Button> : null}
        <div className="console-toolbar-spacer" />
        <Button
          icon={<DownloadOutlined />}
          href={usageExportUrl(query)}
          target="_blank"
          rel="noreferrer"
        >
          导出 CSV
        </Button>
      </div>

      {data && data.unpriced_models.length > 0 ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="以下上游模型未配置价格，成本按 0 计"
          description={data.unpriced_models.map((name) => (
            <Tag key={name}>{name}</Tag>
          ))}
        />
      ) : null}

      {usageQuery.isError ? (
        <div className="console-error-panel" role="alert">
          <div className="console-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: '#dc2626' }}
              aria-hidden="true"
            />
            <strong>加载用量失败</strong>
          </div>
          <p className="console-error-desc">
            {usageQuery.error instanceof Error ? usageQuery.error.message : '未知错误'}
          </p>
          <Button size="small" onClick={() => void usageQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : usageQuery.isPending ? (
        <ListSkeleton />
      ) : (
        <>
          <div className="console-surface" style={{ padding: 16, marginBottom: 16 }}>
            {data && data.trend.length > 0 ? (
              <Chart option={trendOption} ariaLabel="按日请求数与成本趋势" height={260} />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="所选区间没有趋势数据" />
            )}
          </div>

          {items.length === 0 ? (
            <div className="console-empty">
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={hasFilters ? '没有匹配的用量记录' : '还没有用量记录'}
              >
                {hasFilters ? (
                  <Button onClick={resetFilters}>清除筛选</Button>
                ) : (
                  <span className="muted" style={{ fontSize: 12 }}>
                    用量由网关调用写入；先发起一次调用，这里才会有记录。
                  </span>
                )}
              </Empty>
            </div>
          ) : isNarrow ? (
            <div className="console-cards">
              {items.map((item, index) => (
                <article className="console-card" key={`${item.date}-${index}`}>
                  <div className="console-card-head">
                    <div>
                      <div className="console-card-name mono">{item.model_alias ?? '未知模型'}</div>
                      <div className="muted console-card-id mono">
                        {item.tenant_id ?? '—'} / {item.project_id ?? '—'}
                      </div>
                    </div>
                    <span className="num">{item.date}</span>
                  </div>
                  <dl className="console-card-meta">
                    <div>
                      <dt>请求</dt>
                      <dd className="num">{formatNumber(item.requests)}</dd>
                    </div>
                    <div>
                      <dt>Tokens（入 / 出 / 总）</dt>
                      <dd className="num">
                        {formatNumber(item.prompt_tokens)} / {formatNumber(item.completion_tokens)}{' '}
                        / {formatNumber(item.total_tokens)}
                      </dd>
                    </div>
                    <div>
                      <dt>Key</dt>
                      <dd className="mono">{item.key_id ?? '—'}</dd>
                    </div>
                    <div>
                      <dt>成本</dt>
                      <dd className="num">{item.cost.toFixed(4)}</dd>
                    </div>
                  </dl>
                </article>
              ))}
              <div className="console-cards-count">共 {items.length} 行</div>
            </div>
          ) : (
            <div className="console-surface console-table">
              <Table<UsageItem>
                rowKey={(record) =>
                  `${record.date}-${record.tenant_id}-${record.project_id}-${record.model_alias}-${record.key_id}`
                }
                size="middle"
                columns={columns}
                dataSource={items}
                scroll={{ x: 1160 }}
                pagination={{ pageSize: 20, showSizeChanger: false }}
              />
            </div>
          )}

          <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
            数据区间：{data?.from ?? '—'} ~ {data?.to ?? '—'} · 共 {items.length} 行
          </p>
        </>
      )}
    </>
  );
}
