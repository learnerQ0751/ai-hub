import { PageHeader } from '@/components/PageHeader';
import { fetchModels } from '@/features/models/api';
import { chatCompletion, ragAnswer, ragSearch, type ChatMessage } from '@/features/playground/api';
import type { RagAnswerResult, RagSearchResult } from '@/features/rag/types';
import { formatDateTime, formatNumber } from '@/features/shared/format';
import { fetchTrace } from '@/features/traces/api';
import type { TraceDetail } from '@/features/traces/types';
import { ReloadOutlined, SendOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
  traceId?: string;
  usage?: { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number };
}

export default function PlaygroundPage() {
  const [searchParams] = useSearchParams();
  const initialProject = searchParams.get('project') ?? '';

  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState<string | undefined>();
  const [systemPrompt, setSystemPrompt] = useState('');
  const [userInput, setUserInput] = useState('');
  const [turns, setTurns] = useState<ChatTurn[]>([]);

  const [projectId, setProjectId] = useState(initialProject);
  const [ragQuery, setRagQuery] = useState('');
  const [topK, setTopK] = useState(5);
  const [searchResult, setSearchResult] = useState<RagSearchResult | null>(null);
  const [answerResult, setAnswerResult] = useState<RagAnswerResult | null>(null);

  const [traceId, setTraceId] = useState<string | null>(null);

  const modelsQuery = useQuery({
    queryKey: ['models', 'list', 'playground'],
    queryFn: () => fetchModels('ok'),
    retry: false,
    staleTime: 60_000,
  });

  const traceQuery = useQuery({
    queryKey: ['trace', traceId],
    queryFn: () => fetchTrace(traceId!),
    enabled: Boolean(traceId),
    retry: false,
  });

  const hasKey = apiKey.trim().length > 0;
  const modelOptions = (modelsQuery.data ?? [])
    .filter((item) => item.status === 'ACTIVE')
    .map((item) => ({ value: item.alias, label: item.alias }));
  // 未显式选模型时用第一个启用的别名，避免把占位名发给网关。
  const effectiveModel = model ?? undefined;

  const chatMutation = useMutation({
    mutationFn: async () => {
      const messages: ChatMessage[] = [
        ...(systemPrompt.trim() ? [{ role: 'system' as const, content: systemPrompt.trim() }] : []),
        ...turns.map((turn) => ({ role: turn.role, content: turn.content })),
        { role: 'user' as const, content: userInput.trim() },
      ];
      return chatCompletion({
        apiKey: apiKey.trim(),
        model: effectiveModel ?? modelOptions[0]?.value ?? 'chat-default',
        messages,
      });
    },
    onSuccess: (result) => {
      setTurns((previous) => [
        ...previous,
        { role: 'user', content: userInput.trim() },
        {
          role: 'assistant',
          content: result.content,
          traceId: result.traceId,
          usage: result.usage,
        },
      ]);
      setUserInput('');
    },
  });

  const searchMutation = useMutation({
    mutationFn: () =>
      ragSearch({
        apiKey: apiKey.trim(),
        projectId: projectId.trim(),
        query: ragQuery.trim(),
        topK,
      }),
    onSuccess: (result) => setSearchResult(result.data),
  });

  const answerMutation = useMutation({
    mutationFn: () =>
      ragAnswer({
        apiKey: apiKey.trim(),
        projectId: projectId.trim(),
        query: ragQuery.trim(),
        model: effectiveModel ?? modelOptions[0]?.value,
      }),
    onSuccess: (result) => setAnswerResult(result.data),
  });

  const traceColumns: TableProps<TraceDetail['events'][number]>['columns'] = [
    {
      title: '分段',
      dataIndex: 'segment',
      width: 96,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 170,
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: number) => <span className="num">{value}</span>,
    },
    {
      title: '耗时',
      dataIndex: 'latency_ms',
      width: 90,
      align: 'right',
      render: (value: number) => <span className="num">{value} ms</span>,
    },
    {
      title: '时间',
      dataIndex: 'created_at',
      width: 160,
      render: (value: string) => <span className="num muted">{formatDateTime(value)}</span>,
    },
    {
      title: '摘要',
      dataIndex: 'payload',
      render: (value: Record<string, unknown>) => (
        <span className="mono muted" style={{ wordBreak: 'break-all' }}>
          {Object.keys(value ?? {}).length ? JSON.stringify(value) : '—'}
        </span>
      ),
    },
  ];

  const keyPanel = (
    <Card size="small" style={{ marginBottom: 16 }}>
      <div className="console-toolbar" style={{ marginBottom: 0 }}>
        <Input.Password
          className="console-search"
          placeholder="临时虚拟 Key（sk-...）"
          value={apiKey}
          onChange={(event) => setApiKey(event.target.value)}
          autoComplete="off"
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          className="console-select"
          popupMatchSelectWidth={false}
          placeholder="模型别名"
          value={model}
          onChange={setModel}
          options={modelOptions}
          notFoundContent={modelsQuery.isError ? '无法加载模型列表' : '没有启用的别名'}
        />
        <div className="console-toolbar-spacer" />
        <span className="muted" style={{ fontSize: 12 }}>
          虚拟 Key 只保存在当前页面内存中，刷新即清空，不写 storage。
        </span>
      </div>
    </Card>
  );

  const chatTab = (
    <>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          系统提示（可选）
        </Typography.Text>
        <Input.TextArea
          rows={2}
          value={systemPrompt}
          onChange={(event) => setSystemPrompt(event.target.value)}
          placeholder="例如：你是 ai-hub 的助手，回答保持简洁。"
        />
        <div style={{ height: 12 }} />
        <Input.TextArea
          rows={3}
          value={userInput}
          onChange={(event) => setUserInput(event.target.value)}
          placeholder="输入要发送的消息"
        />
        <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={chatMutation.isPending}
            disabled={!hasKey || !userInput.trim()}
            onClick={() => chatMutation.mutate()}
          >
            发送
          </Button>
          <Button disabled={turns.length === 0} onClick={() => setTurns([])}>
            清空对话
          </Button>
        </div>
      </Card>

      {chatMutation.isError ? (
        <div className="console-error-panel" role="alert" style={{ marginBottom: 12 }}>
          <div className="console-error-head">
            <strong>调用失败</strong>
          </div>
          <p className="console-error-desc">
            {chatMutation.error instanceof Error ? chatMutation.error.message : '未知错误'}
          </p>
        </div>
      ) : null}

      {turns.length === 0 ? (
        <div className="console-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="填入临时 Key 后发一条消息试试" />
        </div>
      ) : (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          {turns.map((turn, index) => (
            <Card
              key={`${turn.role}-${index}`}
              size="small"
              title={turn.role === 'user' ? '用户' : '助手'}
              extra={
                turn.traceId ? (
                  <Button size="small" type="link" onClick={() => setTraceId(turn.traceId!)}>
                    查看链路
                  </Button>
                ) : null
              }
            >
              <div style={{ whiteSpace: 'pre-wrap' }}>{turn.content || '（空响应）'}</div>
              {turn.usage ? (
                <div className="muted num" style={{ marginTop: 8, fontSize: 12 }}>
                  Tokens 入 {formatNumber(turn.usage.prompt_tokens)} / 出{' '}
                  {formatNumber(turn.usage.completion_tokens)} / 总{' '}
                  {formatNumber(turn.usage.total_tokens)}
                </div>
              ) : null}
              {turn.traceId ? (
                <div className="mono muted" style={{ marginTop: 4, fontSize: 12 }}>
                  trace: {turn.traceId}
                </div>
              ) : null}
            </Card>
          ))}
        </Space>
      )}
    </>
  );

  const ragTab = (
    <>
      <Card size="small" style={{ marginBottom: 12 }}>
        <div className="console-toolbar" style={{ marginBottom: 0 }}>
          <Input
            className="console-search"
            placeholder="项目 ID"
            value={projectId}
            onChange={(event) => setProjectId(event.target.value)}
          />
          <Input
            className="console-search"
            placeholder="检索问题"
            value={ragQuery}
            onChange={(event) => setRagQuery(event.target.value)}
            onPressEnter={() => {
              if (hasKey && projectId.trim() && ragQuery.trim()) {
                searchMutation.mutate();
              }
            }}
          />
          <InputNumber min={1} max={20} value={topK} onChange={(value) => setTopK(value ?? 5)} />
          <Button
            type="primary"
            loading={searchMutation.isPending}
            disabled={!hasKey || !projectId.trim() || !ragQuery.trim()}
            onClick={() => searchMutation.mutate()}
          >
            检索
          </Button>
        </div>
      </Card>

      {searchMutation.isError ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            searchMutation.error instanceof Error ? searchMutation.error.message : '检索失败'
          }
        />
      ) : null}

      {searchResult ? (
        <>
          <div className="console-stat-strip">
            <span className="console-stat-item">
              命中 <b className="console-stat-value">{searchResult.count}</b> 条
            </span>
            <span className="console-stat-item">{searchResult.no_hit ? '无命中' : '有命中'}</span>
            <span className="console-stat-item">{searchResult.reranked ? '已重排' : '未重排'}</span>
            <span className="console-stat-note">gateway /v1/rag/search</span>
          </div>
          {searchResult.results.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有命中切片" />
          ) : (
            <Space direction="vertical" style={{ width: '100%' }} size={8}>
              {searchResult.results.map((hit) => (
                <Card
                  key={hit.chunk_id}
                  size="small"
                  title={
                    <span>
                      <span className="mono">{hit.source}</span>
                      <span className="muted" style={{ marginLeft: 8 }}>
                        #{hit.chunk_index}
                      </span>
                    </span>
                  }
                  extra={
                    <Space size={4}>
                      <Tag>{hit.match_type}</Tag>
                      <span className="num">score {hit.score.toFixed(3)}</span>
                    </Space>
                  }
                >
                  <div className="muted" style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>
                    {hit.content}
                  </div>
                  <div className="num muted" style={{ marginTop: 6, fontSize: 12 }}>
                    向量 {hit.vector_score ?? '—'} · 字面 {hit.keyword_score ?? '—'} · RRF{' '}
                    {hit.rrf_score ?? '—'}
                  </div>
                </Card>
              ))}
            </Space>
          )}
        </>
      ) : (
        <div className="console-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="填项目 ID 与问题后点检索" />
        </div>
      )}
    </>
  );

  const answerTab = (
    <>
      <Card size="small" style={{ marginBottom: 12 }}>
        <div className="console-toolbar" style={{ marginBottom: 0 }}>
          <Input
            className="console-search"
            placeholder="项目 ID"
            value={projectId}
            onChange={(event) => setProjectId(event.target.value)}
          />
          <Input
            className="console-search"
            placeholder="问题"
            value={ragQuery}
            onChange={(event) => setRagQuery(event.target.value)}
          />
          <Button
            type="primary"
            loading={answerMutation.isPending}
            disabled={!hasKey || !projectId.trim() || !ragQuery.trim()}
            onClick={() => answerMutation.mutate()}
          >
            生成回答
          </Button>
          <div className="console-toolbar-spacer" />
          <span className="muted" style={{ fontSize: 12 }}>
            使用所选模型别名，未选则由服务端默认
          </span>
        </div>
      </Card>

      {answerMutation.isError ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            answerMutation.error instanceof Error ? answerMutation.error.message : '生成失败'
          }
        />
      ) : null}

      {answerResult ? (
        <>
          <Card size="small" title="回答">
            <div style={{ whiteSpace: 'pre-wrap' }}>{answerResult.answer}</div>
            <div className="muted num" style={{ marginTop: 12, fontSize: 12 }}>
              模型 {answerResult.model || '—'} ·{' '}
              {answerResult.no_hit ? '未命中知识库' : '命中知识库'}
              {answerResult.free_answer ? ' · 自由回答' : ''} · Tokens{' '}
              {formatNumber(answerResult.usage?.total_tokens)}
            </div>
          </Card>
          <Card size="small" title="引用" style={{ marginTop: 12 }}>
            {answerResult.citations.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有引用（自由回答）" />
            ) : (
              <Table
                rowKey="chunk_id"
                size="small"
                pagination={false}
                dataSource={answerResult.citations}
                columns={[
                  { title: '#', dataIndex: 'index', width: 56 },
                  {
                    title: '来源',
                    dataIndex: 'source',
                    render: (value: string) => <span className="mono">{value}</span>,
                  },
                  {
                    title: '切片',
                    dataIndex: 'chunk_id',
                    render: (value: string) => <span className="mono">{value}</span>,
                  },
                  {
                    title: '分数',
                    dataIndex: 'score',
                    width: 90,
                    align: 'right',
                    render: (value: number) => <span className="num">{value.toFixed(3)}</span>,
                  },
                ]}
              />
            )}
          </Card>
        </>
      ) : (
        <div className="console-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="填问题后点生成回答" />
        </div>
      )}
    </>
  );

  return (
    <>
      <PageHeader
        title="Playground"
        section="调试"
        description="对话、RAG 检索与问答调试；请求直接发往 gateway，需要一把虚拟 Key。"
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              setTurns([]);
              setSearchResult(null);
              setAnswerResult(null);
            }}
          >
            清空结果
          </Button>
        }
      />

      {!hasKey ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="先填入一把虚拟 Key（sk-...），用于向 gateway 发起调试请求"
          description="Key 只存在于当前页面内存，不落 storage、不进构建产物；控制台会话 cookie 不能用于数据面。"
        />
      ) : null}

      {keyPanel}

      <Tabs
        items={[
          { key: 'chat', label: '对话', children: chatTab },
          { key: 'search', label: 'RAG 检索', children: ragTab },
          { key: 'answer', label: 'RAG 问答', children: answerTab },
        ]}
      />

      <Drawer
        title={traceId ? `链路 · ${traceId}` : '链路'}
        open={Boolean(traceId)}
        onClose={() => setTraceId(null)}
        width={860}
      >
        {traceQuery.isPending ? (
          <Spin />
        ) : traceQuery.isError ? (
          <div className="console-error-panel" role="alert">
            <div className="console-error-head">
              <strong>加载链路失败</strong>
            </div>
            <p className="console-error-desc">
              {traceQuery.error instanceof Error ? traceQuery.error.message : '未知错误'}
            </p>
          </div>
        ) : traceQuery.data ? (
          <>
            <Descriptions
              size="small"
              column={2}
              bordered
              items={[
                {
                  key: 'id',
                  label: 'trace_id',
                  children: <span className="mono">{traceQuery.data.trace_id}</span>,
                },
                { key: 'count', label: '事件数', children: traceQuery.data.count },
              ]}
            />
            <Table
              rowKey={(record) => `${record.segment}-${record.name}-${record.created_at}`}
              size="small"
              style={{ marginTop: 12 }}
              columns={traceColumns}
              dataSource={traceQuery.data.events}
              pagination={false}
              scroll={{ x: 760 }}
            />
          </>
        ) : null}
      </Drawer>
    </>
  );
}
