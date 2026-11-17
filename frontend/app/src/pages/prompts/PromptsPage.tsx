import { PageHeader } from '@/components/PageHeader';
import { fetchPromptVersions, fetchPrompts, publishPromptVersion } from '@/features/prompts/api';
import {
  PROMPT_STATUS_DOT_COLOR,
  PROMPT_STATUS_LABEL,
  promptStatusOf,
} from '@/features/prompts/types';
import type { PromptVersion } from '@/features/prompts/types';
import { formatDateTime } from '@/features/shared/format';
import type { MockMode } from '@/features/shared/mock';
import { useIsNarrow } from '@/hooks/useIsNarrow';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Popconfirm,
  Skeleton,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';

function StatusText({ value }: { value: string }) {
  const status = promptStatusOf(value);
  return (
    <span className="status-inline">
      <span
        className="status-dot"
        style={{ backgroundColor: PROMPT_STATUS_DOT_COLOR[status] }}
        aria-hidden="true"
      />
      {PROMPT_STATUS_LABEL[status]}
    </span>
  );
}

function ListSkeleton() {
  return (
    <div
      className="console-surface console-skeleton"
      aria-busy="true"
      aria-label="正在加载 Prompt 模板"
    >
      <div className="console-skeleton-label">正在加载 Prompt 模板…</div>
      <Skeleton active title={false} paragraph={{ rows: 8, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

interface PromptsPageProps {
  initialDemoMode?: MockMode;
}

export default function PromptsPage({ initialDemoMode }: PromptsPageProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const isNarrow = useIsNarrow();

  const [tenantInput, setTenantInput] = useState('');
  const [tenant, setTenant] = useState<string | undefined>();
  const [detail, setDetail] = useState<{ prompt: PromptVersion; tenantId?: string } | null>(null);
  const [activeVersion, setActiveVersion] = useState<PromptVersion | null>(null);

  const promptsQuery = useQuery({
    queryKey: ['prompts', 'list', tenant],
    queryFn: () => fetchPrompts(tenant),
    retry: false,
  });

  const versionsQuery = useQuery({
    queryKey: ['prompts', 'versions', detail?.prompt.name, detail?.tenantId],
    queryFn: () => fetchPromptVersions(detail!.prompt.name, detail?.tenantId),
    enabled: Boolean(detail),
    retry: false,
  });

  const publishMutation = useMutation({
    mutationFn: (variables: { name: string; version: number; tenantId?: string }) =>
      publishPromptVersion(variables.name, variables.version, variables.tenantId),
    onSuccess: (published) => {
      message.success(`已发布 v${published.version}：${published.name}`);
      setDetail((current) =>
        current && current.prompt.name === published.name
          ? { ...current, prompt: published }
          : current,
      );
      setActiveVersion(published);
      void queryClient.invalidateQueries({ queryKey: ['prompts'] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const prompts = useMemo(() => promptsQuery.data ?? [], [promptsQuery.data]);
  const visiblePrompts = initialDemoMode === 'empty' ? [] : prompts;
  const versions = useMemo(() => versionsQuery.data ?? [], [versionsQuery.data]);

  const hasTenantFilter = Boolean(tenant);
  const isPublishing = (version: number) =>
    publishMutation.isPending && publishMutation.variables?.version === version;

  const openDetail = (prompt: PromptVersion) => {
    setDetail({ prompt, tenantId: tenant });
    setActiveVersion(prompt);
  };

  const closeDetail = () => {
    setDetail(null);
    setActiveVersion(null);
  };

  const columns: TableProps<PromptVersion>['columns'] = [
    {
      title: '模板',
      key: 'name',
      fixed: 'left',
      width: 240,
      render: (_value, record) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.name}</div>
          <div className="mono muted">v{record.version}</div>
        </div>
      ),
    },
    {
      title: '租户',
      dataIndex: 'tenantId',
      width: 136,
      render: (value?: string | null) => <span className="mono">{value ?? '—'}</span>,
    },
    {
      title: '版本',
      dataIndex: 'version',
      width: 88,
      render: (value: number) => <span className="num">v{value}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 104,
      render: (value: string) => <StatusText value={value} />,
    },
    {
      title: '变量',
      dataIndex: 'variables',
      width: 180,
      render: (value: string[]) =>
        value.length ? (
          <Space size={4} wrap>
            {value.map((item) => (
              <Tag key={item} style={{ marginInlineEnd: 0 }}>
                {item}
              </Tag>
            ))}
          </Space>
        ) : (
          <span className="muted">—</span>
        ),
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      width: 120,
      render: (value?: string | null) => value ?? <span className="muted">—</span>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 156,
      render: (value: string) => <span className="num muted">{formatDateTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 108,
      fixed: 'right',
      render: (_value, record) => (
        <Button size="small" onClick={() => openDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const versionColumns: TableProps<PromptVersion>['columns'] = [
    {
      title: '版本',
      dataIndex: 'version',
      width: 72,
      render: (value: number) => <span className="num">v{value}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => <StatusText value={value} />,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 150,
      render: (value: string) => <span className="num muted">{formatDateTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_value, record) => {
        const published = promptStatusOf(record.status) === 'PUBLISHED';
        return (
          <Space size={4} wrap>
            <Button size="small" onClick={() => setActiveVersion(record)}>
              查看
            </Button>
            <Popconfirm
              title={published ? '该版本已是发布版本' : `发布 v${record.version}？`}
              description={published ? null : '发布旧版本等同回滚；当前发布版本会归档。'}
              okText="发布"
              cancelText="取消"
              disabled={published}
              onConfirm={() =>
                publishMutation.mutate({
                  name: record.name,
                  version: record.version,
                  tenantId: detail?.tenantId,
                })
              }
            >
              <Button
                size="small"
                type="primary"
                disabled={published}
                loading={isPublishing(record.version)}
              >
                {published ? '已发布' : '发布'}
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  const showError =
    initialDemoMode === 'error' || (initialDemoMode === undefined && promptsQuery.isError);
  const showLoading =
    initialDemoMode === 'loading' || (initialDemoMode === undefined && promptsQuery.isLoading);

  const publishedCount = visiblePrompts.filter(
    (prompt) => promptStatusOf(prompt.status) === 'PUBLISHED',
  ).length;
  const statText = (value: number) => (showError || showLoading ? '—' : value);

  return (
    <>
      <PageHeader
        title="Prompt 模板"
        section="能力目录"
        description="模板版本历史与发布回滚；数据来自 console /v1/prompts（契约 §4.4），列表只含每个模板的最新发布版本。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void promptsQuery.refetch()}>
            刷新
          </Button>
        }
      />

      <div className="console-stat-strip">
        <span className="console-stat-item">
          共 <b className="console-stat-value">{statText(visiblePrompts.length)}</b> 个模板
        </span>
        <span className="console-stat-item">
          已发布 <b className="console-stat-value">{statText(publishedCount)}</b>
        </span>
        <span className="console-stat-item">
          租户 <b className="console-stat-value">{tenant ?? '全部（master 视角）'}</b>
        </span>
        <span className="console-stat-note">真实接口 · 契约 §4.4</span>
      </div>

      <div className="console-toolbar">
        <Input.Search
          allowClear
          className="console-search"
          prefix={<SearchOutlined />}
          placeholder="租户 ID（精确匹配，可留空）"
          value={tenantInput}
          onChange={(event) => setTenantInput(event.target.value)}
          onSearch={(value) => setTenant(value.trim() || undefined)}
          enterButton="查询"
        />
        {hasTenantFilter ? (
          <Button
            onClick={() => {
              setTenantInput('');
              setTenant(undefined);
            }}
          >
            清除租户筛选
          </Button>
        ) : null}
      </div>

      {showError ? (
        <div className="console-error-panel" role="alert">
          <div className="console-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: PROMPT_STATUS_DOT_COLOR.ARCHIVED }}
              aria-hidden="true"
            />
            <strong>加载 Prompt 模板失败</strong>
          </div>
          <p className="console-error-desc">
            {initialDemoMode === 'error'
              ? '演示状态：请求失败时显示重试入口。'
              : promptsQuery.error instanceof Error
                ? promptsQuery.error.message
                : '未知错误'}
          </p>
          <Button size="small" onClick={() => void promptsQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : showLoading ? (
        <ListSkeleton />
      ) : visiblePrompts.length === 0 ? (
        <div className="console-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              hasTenantFilter ? '该租户下没有已发布的 Prompt 模板' : '还没有已发布的 Prompt 模板'
            }
          >
            {hasTenantFilter ? (
              <Button
                onClick={() => {
                  setTenantInput('');
                  setTenant(undefined);
                }}
              >
                清除租户筛选
              </Button>
            ) : (
              <span className="muted" style={{ fontSize: 12 }}>
                列表只返回已发布模板；可先通过 POST /v1/prompts 创建并发布。
              </span>
            )}
          </Empty>
        </div>
      ) : isNarrow ? (
        <div className="console-cards">
          {visiblePrompts.map((prompt) => (
            <article className="console-card" key={prompt.name}>
              <div className="console-card-head">
                <div>
                  <div className="console-card-name">{prompt.name}</div>
                  <div className="mono muted console-card-id">v{prompt.version}</div>
                </div>
                <StatusText value={prompt.status} />
              </div>
              <dl className="console-card-meta">
                <div>
                  <dt>租户</dt>
                  <dd className="mono">{prompt.tenantId ?? '—'}</dd>
                </div>
                <div>
                  <dt>变量</dt>
                  <dd>{prompt.variables.join('、') || '—'}</dd>
                </div>
                <div>
                  <dt>创建人</dt>
                  <dd>{prompt.createdBy ?? '—'}</dd>
                </div>
                <div>
                  <dt>创建时间</dt>
                  <dd className="num">{formatDateTime(prompt.createdAt)}</dd>
                </div>
              </dl>
              <div className="console-card-actions">
                <Button size="small" onClick={() => openDetail(prompt)}>
                  详情与版本
                </Button>
              </div>
            </article>
          ))}
          <div className="console-cards-count">共 {visiblePrompts.length} 个模板</div>
        </div>
      ) : (
        <div className="console-surface console-table">
          <Table<PromptVersion>
            rowKey={(record) => `${record.tenantId ?? ''}:${record.name}`}
            size="middle"
            columns={columns}
            dataSource={visiblePrompts}
            scroll={{ x: 1160 }}
            pagination={false}
          />
        </div>
      )}

      <Drawer
        title={detail ? detail.prompt.name : 'Prompt 模板'}
        open={Boolean(detail)}
        onClose={closeDetail}
        width={isNarrow ? '100%' : 640}
      >
        {detail ? (
          <>
            <Descriptions
              size="small"
              column={1}
              bordered
              items={[
                {
                  key: 'tenant',
                  label: '租户',
                  children: <span className="mono">{detail.prompt.tenantId ?? '—'}</span>,
                },
                {
                  key: 'published',
                  label: '当前发布版本',
                  children: <span className="num">v{detail.prompt.version}</span>,
                },
                {
                  key: 'status',
                  label: '状态',
                  children: <StatusText value={detail.prompt.status} />,
                },
                { key: 'createdBy', label: '创建人', children: detail.prompt.createdBy ?? '—' },
                {
                  key: 'createdAt',
                  label: '创建时间',
                  children: <span className="num">{formatDateTime(detail.prompt.createdAt)}</span>,
                },
                {
                  key: 'variables',
                  label: '变量声明',
                  children: detail.prompt.variables.length ? (
                    <Space size={4} wrap>
                      {detail.prompt.variables.map((item) => (
                        <Tag key={item} style={{ marginInlineEnd: 0 }}>
                          {item}
                        </Tag>
                      ))}
                    </Space>
                  ) : (
                    '—'
                  ),
                },
              ]}
            />

            <h4 style={{ margin: '20px 0 8px' }}>
              版本历史
              {versionsQuery.isFetching ? <span className="muted"> · 加载中…</span> : null}
            </h4>
            {versionsQuery.isError ? (
              <div className="console-error-panel" role="alert">
                <div className="console-error-head">
                  <strong>加载版本历史失败</strong>
                </div>
                <p className="console-error-desc">
                  {versionsQuery.error instanceof Error ? versionsQuery.error.message : '未知错误'}
                </p>
                <Button size="small" onClick={() => void versionsQuery.refetch()}>
                  重试
                </Button>
              </div>
            ) : versionsQuery.isLoading ? (
              <Skeleton active title={false} paragraph={{ rows: 3 }} />
            ) : versions.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有版本记录" />
            ) : (
              <Table<PromptVersion>
                rowKey="version"
                size="small"
                columns={versionColumns}
                dataSource={versions}
                pagination={false}
                locale={{ emptyText: '没有版本记录' }}
              />
            )}

            <h4 style={{ margin: '20px 0 8px' }}>
              {activeVersion ? `v${activeVersion.version} 内容` : '模板内容'}
              {activeVersion ? <StatusText value={activeVersion.status} /> : null}
            </h4>
            <pre className="console-code console-code-tall">
              {(activeVersion ?? versions[0] ?? detail.prompt).content}
            </pre>
          </>
        ) : null}
      </Drawer>
    </>
  );
}
