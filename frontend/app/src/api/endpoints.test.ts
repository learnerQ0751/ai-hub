import { describe, expect, it } from 'vitest';
import { endpoints, withQuery } from './endpoints';

describe('endpoints', () => {
  it('matches the frozen console contract for keys and models', () => {
    expect(endpoints.keys.list).toBe('/v1/admin/keys');
    expect(endpoints.keys.block('abc')).toBe('/v1/admin/keys/abc/block');
    expect(endpoints.keys.unblock('abc')).toBe('/v1/admin/keys/abc/unblock');
    expect(endpoints.models.list).toBe('/v1/admin/models');
    expect(endpoints.models.detail('qwen-plus')).toBe('/v1/admin/models/qwen-plus');
    expect(endpoints.models.deployments('qwen-plus')).toBe(
      '/v1/admin/models/qwen-plus/deployments',
    );
  });

  it('uses the frozen paths for MCP, RAG, traces and projects', () => {
    expect(endpoints.mcpServers.list).toBe('/v1/admin/mcp/servers');
    expect(endpoints.mcpServers.remove('srv-1')).toBe('/v1/admin/mcp/servers/srv-1');
    expect(endpoints.mcpServers.reprobe('srv-1')).toBe('/v1/admin/mcp/servers/srv-1/reprobe');
    expect(endpoints.mcpServers.runtimeStats).toBe('/v1/admin/mcp/runtime/stats');
    expect(endpoints.rag.documents).toBe('/v1/admin/rag/documents');
    expect(endpoints.rag.reindex('doc-1')).toBe('/v1/admin/rag/documents/doc-1/reindex');
    expect(endpoints.traces.detail('t-1')).toBe('/v1/admin/traces/t-1');
    expect(endpoints.projects.authorize).toBe('/v1/admin/project-authorizations');
  });

  it('matches the frozen console contract for prompts, logs and audit', () => {
    expect(endpoints.prompts.list).toBe('/v1/admin/prompts');
    expect(endpoints.prompts.detail('greeting')).toBe('/v1/admin/prompts/greeting');
    expect(endpoints.prompts.versions('greeting')).toBe('/v1/admin/prompts/greeting/versions');
    expect(endpoints.prompts.publish('greeting')).toBe('/v1/admin/prompts/greeting/publish');
    expect(endpoints.logs.list).toBe('/v1/admin/logs');
    // 管理面端点统一在 /v1/admin 下，会话 cookie 才带得到（API-PATHS §4.6）。
    expect(endpoints.audit.list).toBe('/v1/admin/audit-logs');
  });

  it('exposes the §7.1 endpoints implemented on 2026-11-17', () => {
    expect(endpoints.traces.list).toBe('/v1/admin/traces');
    expect(endpoints.rag.chunks('doc-1')).toBe('/v1/admin/rag/documents/doc-1/chunks');
    expect(endpoints.projects.list).toBe('/v1/admin/projects');
    expect(endpoints.keys.detail('abc')).toBe('/v1/admin/keys/abc');
    expect(endpoints.usage.query).toBe('/v1/admin/usage');
  });

  it('encodes path segments and builds queries without empty values', () => {
    expect(endpoints.models.detail('a/b')).toBe('/v1/admin/models/a%2Fb');
    expect(withQuery('/v1/admin/rag/documents', { project_id: 'kb', limit: 20 })).toBe(
      '/v1/admin/rag/documents?project_id=kb&limit=20',
    );
    expect(withQuery('/v1/admin/keys', { tenant_id: undefined })).toBe('/v1/admin/keys');
  });
});
