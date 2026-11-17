import { GATEWAY_BASE_URL } from '@/api/endpoints';
import type { RagAnswerResult, RagSearchResult } from '@/features/rag/types';

/**
 * 数据面（gateway）调试请求。
 * 虚拟 Key 只在本页 React state 中临时存在，不写入 storage、不随构建产物分发。
 */
export class GatewayError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'GatewayError';
    this.status = status;
  }
}

async function gatewayRequest<T>(
  path: string,
  apiKey: string,
  body: unknown,
): Promise<{ data: T; traceId?: string }> {
  let response: Response;
  try {
    response = await fetch(`${GATEWAY_BASE_URL}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify(body),
    });
  } catch {
    throw new GatewayError(0, '无法连接数据面网关，请检查网络或网关地址');
  }

  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      const payload = (await response.json()) as { error?: { message?: string } };
      if (payload.error?.message) {
        message = payload.error.message;
      }
    } catch {
      // 非 JSON 错误体，保留 HTTP 状态说明。
    }
    throw new GatewayError(response.status, message);
  }

  const traceId = response.headers.get('X-Trace-Id') ?? undefined;
  return { data: (await response.json()) as T, traceId };
}

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface ChatResult {
  content: string;
  traceId?: string;
  usage?: { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number };
}

export async function chatCompletion(input: {
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature?: number;
}): Promise<ChatResult> {
  const { data, traceId } = await gatewayRequest<{
    choices?: { message?: { content?: string } }[];
    usage?: ChatResult['usage'];
  }>('/v1/chat/completions', input.apiKey, {
    model: input.model,
    messages: input.messages,
    temperature: input.temperature,
  });
  return {
    content: data.choices?.[0]?.message?.content ?? '',
    traceId,
    usage: data.usage,
  };
}

export async function ragSearch(input: {
  apiKey: string;
  projectId: string;
  query: string;
  topK?: number;
}): Promise<{ data: RagSearchResult; traceId?: string }> {
  const { data, traceId } = await gatewayRequest<RagSearchResult>('/v1/rag/search', input.apiKey, {
    project_id: input.projectId,
    query: input.query,
    top_k: input.topK ?? 5,
  });
  return { data, traceId };
}

export interface EmbeddingResult {
  data: { embedding: number[]; index?: number }[];
  model?: string;
  usage?: { prompt_tokens?: number; total_tokens?: number };
}

export async function ragAnswer(input: {
  apiKey: string;
  projectId: string;
  query: string;
  model?: string;
}): Promise<{ data: RagAnswerResult; traceId?: string }> {
  const { data, traceId } = await gatewayRequest<RagAnswerResult>('/v1/rag/answer', input.apiKey, {
    project_id: input.projectId,
    query: input.query,
    model: input.model,
  });
  return { data, traceId };
}
