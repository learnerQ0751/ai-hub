import { describe, expect, it } from 'vitest';
import { MCP_SERVERS } from './fixtures';
import { filterMcpServers, normalizeMcpStatus } from './types';

describe('normalizeMcpStatus', () => {
  it('maps backend ACTIVE to UP and DOWN to DOWN', () => {
    expect(normalizeMcpStatus('ACTIVE')).toBe('UP');
    expect(normalizeMcpStatus('DOWN')).toBe('DOWN');
    expect(normalizeMcpStatus('active')).toBe('UP');
  });

  it('falls back to UNKNOWN for missing or unrecognised values', () => {
    expect(normalizeMcpStatus(undefined)).toBe('UNKNOWN');
    expect(normalizeMcpStatus('WHATEVER')).toBe('UNKNOWN');
  });
});

describe('filterMcpServers', () => {
  it('returns every server when no filter is set', () => {
    expect(filterMcpServers(MCP_SERVERS, {})).toHaveLength(MCP_SERVERS.length);
  });

  it('filters by tenant and status together', () => {
    const result = filterMcpServers(MCP_SERVERS, { tenantId: 'platform', status: 'DOWN' });
    expect(result.map((server) => server.name)).toEqual(['sql-ops']);
  });

  it('matches keyword against name, server id and endpoint', () => {
    expect(filterMcpServers(MCP_SERVERS, { keyword: 'browser' })[0]?.name).toBe('browser-tools');
    expect(filterMcpServers(MCP_SERVERS, { keyword: 'srv-docs-05' })[0]?.name).toBe('doc-reader');
    expect(filterMcpServers(MCP_SERVERS, { keyword: 'legacy.internal' })[0]?.name).toBe(
      'legacy-sse',
    );
  });
});
