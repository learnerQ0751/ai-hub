import { PageHeader } from '@/components/PageHeader';
import { createModel, deleteModel, fetchModels, updateModel } from '@/features/models/api';
import { emptyModelForm, toAliasPayload, toModelFormValues } from '@/features/models/form';
import type { ModelFormValues } from '@/features/models/form';
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
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';

const ALIAS_RULE = /^[A-Za-z0-9._-]{1,128}$/;
const DEPLOYMENT_KEY_RULE = /^[A-Za-z0-9._#-]{1,64}$/;

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
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const isNarrow = useIsNarrow();

  const [search, setSearch] = useState('');
  const [tenant, setTenant] = useState<string | undefined>();
  const [status, setStatus] = useState<ModelStatus | undefined>();
  const [mockMode, setMockMode] = useState<MockMode>(initialMockMode);
  const [detail, setDetail] = useState<AdminModel | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<AdminModel | null>(null);
  const [form] = Form.useForm<ModelFormValues>();

  const modelsQuery = useQuery({
    queryKey: ['models', 'list', mockMode, tenant, status],
    queryFn: () => fetchModels(mockMode, { tenant_id: tenant, status }),
    retry: false,
    placeholderData: (previous) => previous,
  });

  const invalidateModels = () => void queryClient.invalidateQueries({ queryKey: ['models'] });

  const saveMutation = useMutation({
    mutationFn: (values: ModelFormValues) => {
      const payload = toAliasPayload(values);
      return editing ? updateModel(editing.alias, payload) : createModel(payload);
    },
    onSuccess: (saved) => {
      message.success(
        editing ? `已更新模型别名：${saved.alias}` : `已创建模型别名：${saved.alias}`,
      );
      setFormOpen(false);
      setEditing(null);
      form.resetFields();
      invalidateModels();
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (alias: string) => deleteModel(alias),
    onSuccess: (_result, alias) => {
      message.success(`已删除模型别名：${alias}`);
      if (detail?.alias === alias) {
        setDetail(null);
      }
      invalidateModels();
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue(emptyModelForm());
    setFormOpen(true);
  };

  const openEdit = (model: AdminModel) => {
    setEditing(model);
    form.resetFields();
    form.setFieldsValue(toModelFormValues(model));
    setFormOpen(true);
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const isDeleting = (alias: string) =>
    deleteMutation.isPending && deleteMutation.variables === alias;

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

  const renderActions = (record: AdminModel) => (
    <Space size={4} wrap>
      <Button size="small" icon={<EyeOutlined />} onClick={() => setDetail(record)}>
        详情
      </Button>
      <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
        编辑
      </Button>
      <Popconfirm
        title={`删除模型别名「${record.alias}」？`}
        description="删除后使用该别名的 Key 白名单与调用会立即失效，不可撤销。"
        okText="删除"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        onConfirm={() => deleteMutation.mutate(record.alias)}
      >
        <Button size="small" danger icon={<DeleteOutlined />} loading={isDeleting(record.alias)}>
          删除
        </Button>
      </Popconfirm>
    </Space>
  );

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
      width: 236,
      fixed: 'right',
      render: (_value, record) => renderActions(record),
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
        description="对外模型别名、描述、价格、fallback 顺序与上游部署实例；数据来自 console /v1/admin/models。"
        extra={
          <Space size={8}>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建模型
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => void modelsQuery.refetch()}>
              刷新
            </Button>
          </Space>
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
        <span className="console-stat-note">当前筛选口径 · 真实接口</span>
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
          notFoundContent="当前列表没有其他租户"
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
            {hasFilters ? (
              <Button onClick={clearFilters}>清除筛选</Button>
            ) : (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                添加第一个模型
              </Button>
            )}
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
              <div className="console-card-actions">{renderActions(model)}</div>
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
            scroll={{ x: 1160 }}
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

      <Modal
        title={editing ? `编辑模型别名：${editing.alias}` : '新建模型别名'}
        open={formOpen}
        onCancel={closeForm}
        onOk={() => form.submit()}
        okText={editing ? '保存修改' : '创建模型'}
        cancelText="取消"
        confirmLoading={saveMutation.isPending}
        width={640}
      >
        <Form<ModelFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Form.Item
              name="alias"
              label="模型别名"
              style={{ flex: '1 1 200px' }}
              extra={editing ? '别名创建后不可修改' : '对外暴露的名字，例如 chat、qwen-plus'}
              rules={[
                { required: true, message: '请填写模型别名' },
                {
                  pattern: ALIAS_RULE,
                  message: '只能包含字母、数字、点、下划线或中划线，最长 128 位',
                },
              ]}
            >
              <Input placeholder="例如 chat" disabled={Boolean(editing)} className="mono" />
            </Form.Item>
            <Form.Item
              name="tenant_id"
              label="租户"
              style={{ flex: '1 1 200px' }}
              extra="留空表示全局别名"
            >
              <Input placeholder="例如 platform" className="mono" />
            </Form.Item>
          </div>
          <Form.Item
            name="description"
            label="描述"
            rules={[{ max: 512, message: '描述最长 512 字符' }]}
          >
            <Input.TextArea rows={2} placeholder="这个别名给谁用、指向什么能力" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Form.Item name="status" label="状态" style={{ flex: '0 0 120px' }}>
              <Select options={statusOptions} />
            </Form.Item>
            <Form.Item
              name="fallbacks"
              label="Fallback 链"
              style={{ flex: '1 1 260px' }}
              extra="主部署不可用时按顺序尝试，留空表示无 fallback"
            >
              <Select
                mode="tags"
                placeholder="输入别名后回车，例如 chat-backup"
                tokenSeparators={[',']}
                options={models
                  .filter((model) => model.alias !== editing?.alias)
                  .map((model) => ({ value: model.alias, label: model.alias }))}
              />
            </Form.Item>
          </div>

          <Typography.Title level={5} style={{ marginTop: 8 }}>
            部署实例
          </Typography.Title>
          <p className="muted" style={{ marginTop: 0, marginBottom: 16, fontSize: 12 }}>
            填写上游地址与上游模型才会创建部署，两项留空则只登记别名。价格不在别名上配置：由主部署的上游模型按价格表推导，未配置时显示「未配置」
            {editing ? `（当前 ${formatPrice(editing)}）` : ''}。
          </p>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Form.Item name="api_base" label="上游地址" style={{ flex: '2 1 260px' }}>
              <Input placeholder="https://…/v1（OpenAI 兼容）" className="mono" />
            </Form.Item>
            <Form.Item
              name="upstream_model"
              label="上游模型"
              style={{ flex: '1 1 160px' }}
              extra="决定价格取值"
            >
              <Input placeholder="例如 qwen-plus" className="mono" />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Form.Item
              name="api_key"
              label="上游密钥"
              style={{ flex: '3 1 240px' }}
              extra={editing ? '留空表示沿用已保存的密钥' : '只写不回显'}
            >
              <Input.Password placeholder="sk-…" autoComplete="new-password" />
            </Form.Item>
            <Form.Item
              name="deployment_key"
              label="部署键"
              style={{ flex: '1 1 160px' }}
              extra="留空自动生成 别名#0"
              rules={[
                {
                  pattern: DEPLOYMENT_KEY_RULE,
                  message: '只能包含字母、数字、点、下划线、# 或中划线，最长 64 位',
                },
              ]}
            >
              <Input placeholder="例如 chat#0" className="mono" />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Form.Item
              name="weight"
              label="权重"
              style={{ flex: '1 1 140px' }}
              extra="同类部署的分配比例"
            >
              <InputNumber min={1} style={{ width: '100%' }} placeholder="1" />
            </Form.Item>
            <Form.Item
              name="priority"
              label="优先级"
              style={{ flex: '1 1 140px' }}
              extra="数值越小越先用"
            >
              <InputNumber min={0} style={{ width: '100%' }} placeholder="0" />
            </Form.Item>
            <Form.Item name="deployment_status" label="部署状态" style={{ flex: '1 1 140px' }}>
              <Select options={statusOptions} />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </>
  );
}
