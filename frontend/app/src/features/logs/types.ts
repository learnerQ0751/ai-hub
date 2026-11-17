/** 调用日志条目，字段与后端 AdminLogResDTO 一致（API-PATHS §4.2 / §4.7）。 */
export interface AdminLog {
  request_id: string;
  trace_id?: string | null;
  key_id?: string | null;
  tenant_id?: string | null;
  project_id?: string | null;
  model_alias?: string | null;
  deployment_model?: string | null;
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  cost?: number | null;
  latency_ms: number;
  status: number;
  error_type?: string | null;
  call_type?: string | null;
  target_name?: string | null;
  created_at: string;
}

/** 与后端 AdminLogPageResDTO 一致。 */
export interface AdminLogPage {
  items: AdminLog[];
  count: number;
  has_more: boolean;
}

/** GET /v1/admin/logs 的冻结参数（§4.7）；时间必须是 ISO-8601。 */
export interface LogListQuery {
  tenant_id?: string;
  project_id?: string;
  model?: string;
  key_id?: string;
  status?: number;
  from?: string;
  to?: string;
  limit: number;
  offset: number;
}

export const LOG_STATUS_OPTIONS: { value: number; label: string }[] = [
  { value: 200, label: '200 成功' },
  { value: 400, label: '400 参数错误' },
  { value: 401, label: '401 未认证' },
  { value: 403, label: '403 无权限' },
  { value: 404, label: '404 未找到' },
  { value: 409, label: '409 冲突' },
  { value: 429, label: '429 限流' },
  { value: 500, label: '500 服务错误' },
];

export function logStatusTone(status: number): 'success' | 'warning' | 'error' {
  if (status >= 200 && status < 300) {
    return 'success';
  }
  if (status === 429) {
    return 'warning';
  }
  return 'error';
}

export function formatCost(cost?: number | null): string {
  if (cost === undefined || cost === null) {
    return '—';
  }
  return cost.toFixed(4);
}

export function formatLatency(latencyMs?: number): string {
  if (latencyMs === undefined || latencyMs === null) {
    return '—';
  }
  return latencyMs >= 1000 ? `${(latencyMs / 1000).toFixed(2)} s` : `${latencyMs} ms`;
}

export function formatTokens(
  log: Pick<AdminLog, 'prompt_tokens' | 'completion_tokens' | 'total_tokens'>,
): string {
  return `${log.prompt_tokens} / ${log.completion_tokens} / ${log.total_tokens}`;
}
