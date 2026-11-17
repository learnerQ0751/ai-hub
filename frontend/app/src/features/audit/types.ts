/** 审计记录，字段与后端 AuditLogResDTO 一致（API-PATHS §4.6 / §4.7）。 */
export interface AuditLog {
  id: number;
  tenant_id?: string | null;
  actor?: string | null;
  action: string;
  target?: string | null;
  before: Record<string, unknown>;
  after: Record<string, unknown>;
  trace_id?: string | null;
  created_at: string;
}

/** 与后端 AuditLogPageResDTO 一致。 */
export interface AuditLogPage {
  items: AuditLog[];
  count: number;
  has_more: boolean;
}

/** GET /v1/admin/audit-logs 的冻结参数（§4.7）；action 精确匹配，target 模糊匹配，时间为 ISO-8601。 */
export interface AuditListQuery {
  tenant_id?: string;
  action?: string;
  target?: string;
  from?: string;
  to?: string;
  limit: number;
  offset: number;
}

/** 后端 AuditRecorder.record 当前写入的动作集合（源码核对 2026-11-17）。 */
export const AUDIT_ACTIONS: { value: string; label: string }[] = [
  { value: 'KEY_GENERATE', label: '生成 Key' },
  { value: 'KEY_BLOCK', label: '封禁 Key' },
  { value: 'KEY_UNBLOCK', label: '解封 Key' },
  { value: 'ALIAS_CREATE', label: '创建模型别名' },
  { value: 'ALIAS_UPDATE', label: '更新模型别名' },
  { value: 'ALIAS_DELETE', label: '删除模型别名' },
  { value: 'ALIAS_DEPLOYMENT_UPSERT', label: '新增/更新部署' },
  { value: 'PROJECT_AUTHORIZE', label: '项目授权' },
  { value: 'MCP_REGISTER', label: '注册 MCP Server' },
  { value: 'MCP_REMOVE', label: '注销 MCP Server' },
  { value: 'PROMPT_CREATE', label: '创建 Prompt' },
  { value: 'PROMPT_VERSION_CREATE', label: '追加 Prompt 版本' },
  { value: 'PROMPT_PUBLISH', label: '发布 Prompt' },
  { value: 'RAG_INGEST', label: 'RAG 文档入库' },
  { value: 'RAG_REINDEX', label: 'RAG 文档重建索引' },
  { value: 'RAG_DELETE', label: 'RAG 文档删除' },
];

const ACTION_LABELS = new Map(AUDIT_ACTIONS.map((item) => [item.value, item.label]));

export function auditActionLabel(action: string): string {
  return ACTION_LABELS.get(action) ?? action;
}

export function formatAuditState(state: Record<string, unknown>): string {
  if (!state || Object.keys(state).length === 0) {
    return '{}';
  }
  return JSON.stringify(state, null, 2);
}
