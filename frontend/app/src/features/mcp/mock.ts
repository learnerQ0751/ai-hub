import { MCP_RUNTIME_STATS, MCP_SERVERS } from '@/features/mcp/fixtures';
import type { McpRuntimeStats, McpServer, McpTransport } from '@/features/mcp/types';

export type McpMockMode = 'ok' | 'empty' | 'error' | 'loading';

export interface RegisterMcpServerInput {
  name: string;
  transport: McpTransport;
  endpoint?: string;
  command?: string;
  args: string[];
  tenantId: string;
  description?: string;
}

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const pending = <T>(): Promise<T> => new Promise<T>(() => {});

const clone = (server: McpServer): McpServer => ({
  ...server,
  args: [...server.args],
});

let store: McpServer[] = MCP_SERVERS.map(clone);

export function resetMcpMockStore(): void {
  store = MCP_SERVERS.map(clone);
}

export async function fetchMcpServers(mode: McpMockMode = 'ok'): Promise<McpServer[]> {
  if (mode === 'loading') {
    return pending<McpServer[]>();
  }
  await delay(500);
  if (mode === 'error') {
    throw new Error('无法连接控制面（mock 故障）');
  }
  if (mode === 'empty') {
    return [];
  }
  return store.map(clone);
}

export async function fetchMcpRuntimeStats(mode: McpMockMode = 'ok'): Promise<McpRuntimeStats> {
  if (mode === 'loading') {
    return pending<McpRuntimeStats>();
  }
  await delay(250);
  return { ...MCP_RUNTIME_STATS };
}

export async function reprobeMcpServer(serverId: string): Promise<McpServer> {
  await delay(800);
  const index = store.findIndex((server) => server.serverId === serverId);
  if (index < 0) {
    throw new Error('MCP Server 不存在');
  }
  const updated: McpServer = {
    ...store[index],
    status: 'UP',
    lastHealthAt: new Date().toISOString(),
    lastError: undefined,
  };
  store[index] = updated;
  return clone(updated);
}

export async function registerMcpServer(input: RegisterMcpServerInput): Promise<McpServer> {
  await delay(700);
  if (store.some((server) => server.name.toLowerCase() === input.name.toLowerCase())) {
    throw new Error('Server 名称已存在（mock）');
  }
  const created: McpServer = {
    serverId: `srv-${Date.now().toString(36)}`,
    name: input.name,
    transport: input.transport,
    endpoint: input.endpoint,
    command: input.command,
    args: input.args,
    tenantId: input.tenantId,
    status: 'UNKNOWN',
    description: input.description,
    createdAt: new Date().toISOString(),
    toolCount: 0,
  };
  store = [created, ...store];
  return clone(created);
}

export async function removeMcpServer(serverId: string): Promise<void> {
  await delay(500);
  store = store.filter((server) => server.serverId !== serverId);
}
