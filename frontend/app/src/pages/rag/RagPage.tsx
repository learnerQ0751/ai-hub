import { PageHeader } from '@/components/PageHeader';
import { fetchProjects } from '@/features/projects/api';
import {
  deleteRagDocument,
  fetchRagChunks,
  fetchRagDocuments,
  reindexRagDocument,
  uploadRagDocument,
} from '@/features/rag/api';
import { formatBytes, indexStatusLabel } from '@/features/rag/types';
import type { RagChunk, RagDocument } from '@/features/rag/types';
import { formatDateTime, formatNumber } from '@/features/shared/format';
import { useIsNarrow } from '@/hooks/useIsNarrow';
import {
  DeleteOutlined,
  ExperimentOutlined,
  ReloadOutlined,
  SyncOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  AutoComplete,
  Button,
  Card,
  Drawer,
  Empty,
  Popconfirm,
  Skeleton,
  Space,
  Table,
  Tag,
  Upload,
} from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

function ListSkeleton() {
  return (
    <div className="console-surface console-skeleton" aria-busy="true" aria-label="正在加载文档">
      <div className="console-skeleton-label">正在加载文档…</div>
      <Skeleton active title={false} paragraph={{ rows: 6, width: ['92%', '74%', '88%', '61%'] }} />
    </div>
  );
}

const statusTone = (status: string) => {
  if (status === 'READY') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'error';
  }
  return 'warning';
};

export default function RagPage() {
  const { message } = App.useApp();
  const isNarrow = useIsNarrow();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialProject = searchParams.get('project') ?? '';

  const [projectInput, setProjectInput] = useState(initialProject);
  const [projectId, setProjectId] = useState<string | undefined>(initialProject || undefined);
  const [chunkDoc, setChunkDoc] = useState<RagDocument | null>(null);

  // 支持 ?project= 深链：URL 参数与页内选择共同决定当前项目，不在 effect 里同步 state。
  const paramProject = searchParams.get('project') ?? undefined;
  const activeProject = projectId ?? paramProject;

  const projectsQuery = useQuery({
    queryKey: ['projects', 'list'],
    queryFn: () => fetchProjects({ limit: 200 }),
    retry: false,
  });

  const documentsQuery = useQuery({
    queryKey: ['rag', 'documents', activeProject],
    queryFn: () => fetchRagDocuments(activeProject!),
    enabled: Boolean(activeProject),
    retry: false,
  });

  const chunksQuery = useQuery({
    queryKey: ['rag', 'chunks', chunkDoc?.doc_id],
    queryFn: () => fetchRagChunks(chunkDoc!.doc_id, chunkDoc!.project_id),
    enabled: Boolean(chunkDoc),
    retry: false,
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadRagDocument(file, activeProject!),
    onSuccess: (doc) => {
      message.success(doc.duplicated ? `已命中去重：${doc.source}` : `已接入：${doc.source}`);
      void queryClient.invalidateQueries({ queryKey: ['rag', 'documents', activeProject] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const reindexMutation = useMutation({
    mutationFn: (doc: RagDocument) => reindexRagDocument(doc.doc_id, doc.project_id),
    onSuccess: (doc) => {
      message.success(`已重建索引：${doc.source}`);
      void queryClient.invalidateQueries({ queryKey: ['rag', 'documents', activeProject] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (doc: RagDocument) => deleteRagDocument(doc.doc_id, doc.project_id),
    onSuccess: (_data, doc) => {
      message.success(`已删除：${doc.source}`);
      if (chunkDoc?.doc_id === doc.doc_id) {
        setChunkDoc(null);
      }
      void queryClient.invalidateQueries({ queryKey: ['rag', 'documents', activeProject] });
    },
    onError: (cause: Error) => message.error(cause.message),
  });

  const documents = documentsQuery.data?.documents ?? [];

  const columns: TableProps<RagDocument>['columns'] = [
    {
      title: '来源文件',
      key: 'source',
      width: 260,
      render: (_value, record) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.source}</div>
          <div className="mono muted">{record.doc_id}</div>
        </div>
      ),
    },
    {
      title: '大小',
      dataIndex: 'size_bytes',
      width: 96,
      align: 'right',
      render: (value: number) => <span className="num">{formatBytes(value)}</span>,
    },
    {
      title: '切片数',
      dataIndex: 'chunk_count',
      width: 88,
      align: 'right',
      render: (value: number) => <span className="num">{formatNumber(value)}</span>,
    },
    {
      title: '索引状态',
      dataIndex: 'index_status',
      width: 110,
      render: (value: string) => (
        <Tag color={statusTone(value)} style={{ marginInlineEnd: 0 }}>
          {indexStatusLabel(value)}
        </Tag>
      ),
    },
    {
      title: '向量模型 / 维度',
      key: 'embedding',
      width: 190,
      render: (_value, record) => (
        <div>
          <div className="mono">{record.embedding_model || '—'}</div>
          <div className="muted" style={{ fontSize: 12 }}>
            {record.embedding_dimension ?? '—'} 维
          </div>
        </div>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updated_at',
      width: 156,
      render: (value: string) => <span className="num muted">{formatDateTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 232,
      fixed: 'right',
      render: (_value, record) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => setChunkDoc(record)}>
            切片
          </Button>
          <Button
            size="small"
            icon={<SyncOutlined />}
            loading={
              reindexMutation.isPending && reindexMutation.variables?.doc_id === record.doc_id
            }
            onClick={() => reindexMutation.mutate(record)}
          >
            重建索引
          </Button>
          <Popconfirm
            title="删除这个文档？"
            description="会级联删除切片与向量，不可恢复。"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteMutation.mutate(record)}
          >
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              loading={
                deleteMutation.isPending && deleteMutation.variables?.doc_id === record.doc_id
              }
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const chunkColumns: TableProps<RagChunk>['columns'] = [
    {
      title: '#',
      dataIndex: 'chunk_index',
      width: 56,
      align: 'right',
      render: (value: number) => <span className="num">{value}</span>,
    },
    {
      title: '切片 ID',
      dataIndex: 'chunk_id',
      width: 200,
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '字符数',
      dataIndex: 'char_count',
      width: 80,
      align: 'right',
      render: (value?: number) => <span className="num">{value ?? '—'}</span>,
    },
    {
      title: '内容',
      dataIndex: 'content',
      render: (value: string) => (
        <div
          className="muted"
          style={{ fontSize: 12, whiteSpace: 'pre-wrap', maxHeight: 96, overflow: 'hidden' }}
        >
          {value}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="RAG 知识库"
        section="能力目录"
        description="按项目接入文档、查看切片、重建索引；数据来自 console /v1/admin/rag/documents。检索与问答调试在 Playground。"
        extra={
          <Space wrap>
            <Button
              icon={<ReloadOutlined />}
              disabled={!activeProject}
              onClick={() => void documentsQuery.refetch()}
            >
              刷新
            </Button>
            <Upload
              showUploadList={false}
              accept=".md,.txt,.pdf,.docx,.csv,.json"
              disabled={!activeProject || uploadMutation.isPending}
              beforeUpload={(file) => {
                uploadMutation.mutate(file as File);
                return false;
              }}
            >
              <Button type="primary" icon={<UploadOutlined />} disabled={!activeProject}>
                上传文档
              </Button>
            </Upload>
            <Button
              icon={<ExperimentOutlined />}
              disabled={!activeProject}
              onClick={() =>
                navigate(`/playground?project=${encodeURIComponent(activeProject ?? '')}`)
              }
            >
              检索调试
            </Button>
          </Space>
        }
      />

      <Card size="small" style={{ marginBottom: 16 }}>
        <div className="console-toolbar" style={{ marginBottom: 0 }}>
          <AutoComplete
            className="console-search"
            placeholder="项目 ID（可从下拉选择，也可直接输入）"
            value={projectInput || paramProject || ''}
            onChange={setProjectInput}
            options={(projectsQuery.data?.items ?? []).map((project) => ({
              value: project.project_id,
              label: `${project.project_id} · ${project.tenant_id ?? '—'}`,
            }))}
            filterOption={(input, option) =>
              String(option?.value ?? '')
                .toLowerCase()
                .includes(input.toLowerCase())
            }
          />
          <Button
            type="primary"
            disabled={!projectInput.trim()}
            onClick={() => setProjectId(projectInput.trim() || undefined)}
          >
            加载文档
          </Button>
          {activeProject ? (
            <Tag color="blue" style={{ marginInlineEnd: 0 }}>
              当前项目：{activeProject}
            </Tag>
          ) : null}
          <div className="console-toolbar-spacer" />
          <span className="muted" style={{ fontSize: 12 }}>
            项目列表：
            {projectsQuery.isError
              ? '端点不可用，手填 project_id 即可'
              : `${projectsQuery.data?.count ?? '—'} 个`}
          </span>
        </div>
        {!projectsQuery.isError && (projectsQuery.data?.items.length ?? 0) > 0 ? (
          <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {(projectsQuery.data?.items ?? []).slice(0, 12).map((project) => (
              <Button
                key={project.project_id}
                size="small"
                type={activeProject === project.project_id ? 'primary' : 'default'}
                onClick={() => {
                  setProjectInput(project.project_id);
                  setProjectId(project.project_id);
                }}
              >
                {project.name || project.project_id}
                <span className="muted" style={{ marginLeft: 6, fontSize: 12 }}>
                  {project.name && project.name !== project.project_id
                    ? `${project.project_id} · `
                    : ''}
                  {project.status === 'ACTIVE' ? '启用' : project.status}
                </span>
              </Button>
            ))}
          </div>
        ) : null}
      </Card>

      {!activeProject ? (
        <div className="console-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="先选择或输入项目 ID，再查看文档"
          />
        </div>
      ) : documentsQuery.isError ? (
        <div className="console-error-panel" role="alert">
          <div className="console-error-head">
            <span
              className="status-dot"
              style={{ backgroundColor: '#dc2626' }}
              aria-hidden="true"
            />
            <strong>加载文档失败</strong>
          </div>
          <p className="console-error-desc">
            {documentsQuery.error instanceof Error ? documentsQuery.error.message : '未知错误'}
          </p>
          <Button size="small" onClick={() => void documentsQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : documentsQuery.isPending ? (
        <ListSkeleton />
      ) : documents.length === 0 ? (
        <div className="console-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="这个项目还没有文档，先上传一份">
            <Upload
              showUploadList={false}
              accept=".md,.txt,.pdf,.docx,.csv,.json"
              beforeUpload={(file) => {
                uploadMutation.mutate(file as File);
                return false;
              }}
            >
              <Button type="primary" icon={<UploadOutlined />} loading={uploadMutation.isPending}>
                上传文档
              </Button>
            </Upload>
          </Empty>
        </div>
      ) : isNarrow ? (
        <div className="console-cards">
          {documents.map((doc) => (
            <article className="console-card" key={doc.doc_id}>
              <div className="console-card-head">
                <div>
                  <div className="console-card-name">{doc.source}</div>
                  <div className="mono muted console-card-id">{doc.doc_id}</div>
                </div>
                <Tag color={statusTone(doc.index_status)}>{indexStatusLabel(doc.index_status)}</Tag>
              </div>
              <dl className="console-card-meta">
                <div>
                  <dt>大小 / 切片</dt>
                  <dd className="num">
                    {formatBytes(doc.size_bytes)} / {doc.chunk_count}
                  </dd>
                </div>
                <div>
                  <dt>向量模型</dt>
                  <dd className="mono">{doc.embedding_model || '—'}</dd>
                </div>
                <div>
                  <dt>更新时间</dt>
                  <dd className="num">{formatDateTime(doc.updated_at)}</dd>
                </div>
              </dl>
              <div className="console-card-actions">
                <Button size="small" onClick={() => setChunkDoc(doc)}>
                  切片
                </Button>
                <Button size="small" onClick={() => reindexMutation.mutate(doc)}>
                  重建
                </Button>
                <Popconfirm
                  title="删除这个文档？"
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => deleteMutation.mutate(doc)}
                >
                  <Button size="small" danger>
                    删除
                  </Button>
                </Popconfirm>
              </div>
            </article>
          ))}
          <div className="console-cards-count">共 {documents.length} 个文档</div>
        </div>
      ) : (
        <div className="console-surface console-table">
          <Table<RagDocument>
            rowKey="doc_id"
            size="middle"
            columns={columns}
            dataSource={documents}
            scroll={{ x: 1180 }}
            pagination={{ pageSize: 10, showSizeChanger: false }}
          />
        </div>
      )}

      {documents.some((doc) => doc.reindex_required) ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 12 }}
          message="部分文档的向量模型与当前 embedding 模型不一致，需要重建索引"
        />
      ) : null}

      <Drawer
        title={chunkDoc ? `切片 · ${chunkDoc.source}` : '切片'}
        open={Boolean(chunkDoc)}
        onClose={() => setChunkDoc(null)}
        width={isNarrow ? '100%' : 760}
      >
        {chunksQuery.isError ? (
          <div className="console-error-panel" role="alert">
            <div className="console-error-head">
              <strong>加载切片失败</strong>
            </div>
            <p className="console-error-desc">
              {chunksQuery.error instanceof Error ? chunksQuery.error.message : '未知错误'}
            </p>
          </div>
        ) : chunksQuery.isPending ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : (chunksQuery.data?.items.length ?? 0) === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="这个文档没有切片" />
        ) : (
          <Table<RagChunk>
            rowKey="chunk_id"
            size="small"
            columns={chunkColumns}
            dataSource={chunksQuery.data?.items ?? []}
            pagination={{ pageSize: 10, showSizeChanger: false }}
          />
        )}
      </Drawer>
    </>
  );
}
