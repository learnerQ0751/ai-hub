import { endpoints } from '@/api/endpoints';
import { request } from '@/api/http';
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

const delay = (ms = 450) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const pending = <T>(): Promise<T> => new Promise<T>(() => {});

/** GET /v1/admin/mcp/servers：注册表全量列表（§4.3）。 */
export async function fetchMcpServers(mode: McpMockMode = 'ok'): Promise<McpServer[]> {
  if (mode === 'loading') {
    return pending<McpServer[]>();
  }
  if (mode === 'error') {
    await delay();
    throw new Error('无法连接控制台服务');
  }
  if (mode === 'empty') {
    await delay();
    return [];
  }
  return request<McpServer[]>(endpoints.mcpServers.list, { method: 'GET' });
}

export async function fetchMcpRuntimeStats(mode: McpMockMode = 'ok'): Promise<McpRuntimeStats> {
  if (mode === 'loading') {
    return pending<McpRuntimeStats>();
  }
  if (mode === 'error') {
    await delay();
    throw new Error('无法连接控制台服务');
  }
  if (mode === 'empty') {
    return { activeStdioProcesses: 0, inFlightProbeHttp: 0, inFlightToolCalls: 0 };
  }
  return request<McpRuntimeStats>(endpoints.mcpServers.runtimeStats, { method: 'GET' });
}

export async function reprobeMcpServer(serverId: string): Promise<McpServer> {
  return request<McpServer>(endpoints.mcpServers.reprobe(serverId), { method: 'POST' });
}

export async function registerMcpServer(input: RegisterMcpServerInput): Promise<McpServer> {
  return request<McpServer>(endpoints.mcpServers.create, {
    method: 'POST',
    body: JSON.stringify({
      name: input.name,
      transport: input.transport,
      endpoint: input.endpoint,
      command: input.command,
      args: input.args,
      tenantId: input.tenantId,
      description: input.description,
    }),
  });
}

export async function removeMcpServer(serverId: string): Promise<void> {
  return request<void>(endpoints.mcpServers.remove(serverId), { method: 'DELETE' });
}
