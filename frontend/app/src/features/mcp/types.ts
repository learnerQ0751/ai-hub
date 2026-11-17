export type McpTransport = 'http' | 'sse' | 'stdio';
export type McpServerStatus = 'UP' | 'DOWN' | 'UNKNOWN';

export interface McpServer {
  serverId: string;
  name: string;
  transport: McpTransport;
  endpoint?: string;
  command?: string;
  args: string[];
  tenantId: string;
  status: McpServerStatus;
  lastHealthAt?: string;
  description?: string;
  lastError?: string;
  createdAt: string;
  toolCount: number;
}

export interface McpRuntimeStats {
  activeStdioProcesses: number;
  inFlightProbeHttp: number;
  inFlightToolCalls: number;
}

export interface McpServerFilters {
  keyword?: string;
  tenantId?: string;
  status?: McpServerStatus;
  transport?: McpTransport;
}

export const MCP_STATUS_LABEL: Record<McpServerStatus, string> = {
  UP: '健康',
  DOWN: '异常',
  UNKNOWN: '未知',
};

export const MCP_STATUS_DOT_COLOR: Record<McpServerStatus, string> = {
  UP: '#16a34a',
  DOWN: '#dc2626',
  UNKNOWN: '#9ca3af',
};

export const MCP_TRANSPORT_LABEL: Record<McpTransport, string> = {
  http: 'HTTP',
  sse: 'SSE',
  stdio: 'stdio',
};

export function filterMcpServers(servers: McpServer[], filters: McpServerFilters): McpServer[] {
  const keyword = filters.keyword?.trim().toLowerCase();

  return servers.filter((server) => {
    if (filters.tenantId && server.tenantId !== filters.tenantId) {
      return false;
    }
    if (filters.status && server.status !== filters.status) {
      return false;
    }
    if (filters.transport && server.transport !== filters.transport) {
      return false;
    }
    if (keyword) {
      const haystack = [
        server.name,
        server.serverId,
        server.endpoint,
        server.command,
        server.description,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      if (!haystack.includes(keyword)) {
        return false;
      }
    }
    return true;
  });
}

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short',
  timeStyle: 'medium',
});

export function formatDateTime(value?: string): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return dateTimeFormatter.format(date);
}
