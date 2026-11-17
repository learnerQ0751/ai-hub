/**
 * 控制台接口路径集中表，口径以 docs/API-PATHS.md（契约冻结 2026-11-17）为准。
 * 路径只允许出现在本文件；页面与 feature 层不得自行拼 URL。
 * 文档标注「未实现」的端点不在此登记（如 Trace 列表、文档切片列表、项目列表）。
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

/** 拼接查询参数；值为 undefined 的参数会被丢弃。 */
export function withQuery(
  path: string,
  params: Record<string, string | number | boolean | undefined>,
): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `${path}?${query}` : path;
}

export const endpoints = {
  session: {
    probe: '/v1/admin/session',
    login: '/v1/admin/session',
    logout: '/v1/admin/session',
  },
  projects: {
    /** 列表端点未实现（API-PATHS §7.1），当前只有新增授权。 */
    authorize: '/v1/admin/project-authorizations',
  },
  keys: {
    list: '/v1/admin/keys',
    block: (keyHash: string) => `/v1/admin/keys/${encodeURIComponent(keyHash)}/block`,
    unblock: (keyHash: string) => `/v1/admin/keys/${encodeURIComponent(keyHash)}/unblock`,
  },
  models: {
    list: '/v1/admin/models',
    create: '/v1/admin/models',
    detail: (alias: string) => `/v1/admin/models/${encodeURIComponent(alias)}`,
    update: (alias: string) => `/v1/admin/models/${encodeURIComponent(alias)}`,
    remove: (alias: string) => `/v1/admin/models/${encodeURIComponent(alias)}`,
    deployments: (alias: string) => `/v1/admin/models/${encodeURIComponent(alias)}/deployments`,
  },
  rag: {
    /** GET/POST 同一路径；GET 必填 project_id，可选 limit（§4.7）。 */
    documents: '/v1/rag/documents',
    remove: (docId: string) => `/v1/rag/documents/${encodeURIComponent(docId)}`,
    reindex: (docId: string) => `/v1/rag/documents/${encodeURIComponent(docId)}/reindex`,
  },
  traces: {
    /** 列表端点未实现（API-PATHS §7.1），只有按 ID 查询。 */
    detail: (traceId: string) => `/v1/traces/${encodeURIComponent(traceId)}`,
  },
  mcpServers: {
    list: '/v1/mcp/servers',
    create: '/v1/mcp/servers',
    remove: (serverId: string) => `/v1/mcp/servers/${encodeURIComponent(serverId)}`,
    reprobe: (serverId: string) => `/v1/mcp/servers/${encodeURIComponent(serverId)}/reprobe`,
    runtimeStats: '/v1/mcp/runtime/stats',
  },
  prompts: {
    /** §4.4：列表只返回每个模板的最新发布版本；tenant_id 可选。 */
    list: '/v1/prompts',
    create: '/v1/prompts',
    detail: (name: string) => `/v1/prompts/${encodeURIComponent(name)}`,
    versions: (name: string) => `/v1/prompts/${encodeURIComponent(name)}/versions`,
    publish: (name: string) => `/v1/prompts/${encodeURIComponent(name)}/publish`,
  },
  logs: {
    /** §4.2 / §4.7：tenant_id、project_id、model、key_id、status、from、to、limit、offset。 */
    list: '/v1/admin/logs',
  },
  audit: {
    /** §4.6 / §4.7：tenant_id、action、target、from、to、limit、offset。 */
    list: '/v1/audit-logs',
  },
} as const;
