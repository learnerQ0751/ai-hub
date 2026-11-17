/**
 * 控制台接口路径集中表。
 * 契约尚未冻结（等 REQ-5.2 / 5.3 / 5.4 收口后一次性冻结），下面的路径是占位口径，
 * 只允许出现在本文件；页面与 feature 层不得自行拼 URL。
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export const endpoints = {
  session: {
    probe: '/v1/admin/session',
    login: '/v1/admin/session',
    logout: '/v1/admin/session',
  },
  projects: {
    list: '/v1/admin/projects',
  },
  keys: {
    list: '/v1/admin/keys',
    detail: (keyHash: string) => `/v1/admin/keys/${encodeURIComponent(keyHash)}`,
  },
  rag: {
    documents: (projectId: string) =>
      `/v1/admin/rag/projects/${encodeURIComponent(projectId)}/documents`,
    chunks: (documentId: string) =>
      `/v1/admin/rag/documents/${encodeURIComponent(documentId)}/chunks`,
  },
  traces: {
    list: '/v1/admin/traces',
    detail: (traceId: string) => `/v1/admin/traces/${encodeURIComponent(traceId)}`,
  },
  mcpServers: {
    list: '/v1/admin/mcp-servers',
  },
} as const;
