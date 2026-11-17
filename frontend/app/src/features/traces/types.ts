/** 与后端 TraceResDTO / TraceEventResDTO 一致（REQ-4.2）。 */
export interface TraceEvent {
  segment: string;
  name: string;
  status: number;
  latency_ms: number;
  payload: Record<string, unknown>;
  created_at: string;
}

export interface TraceDetail {
  trace_id: string;
  count: number;
  events: TraceEvent[];
}

/** GET /v1/traces 的列表行（§7.1 已实现）。 */
export interface TraceSummary {
  trace_id: string;
  tenant_id?: string | null;
  project_id?: string | null;
  key_id?: string | null;
  status: number;
  latency_ms: number;
  event_count: number;
  created_at: string;
}

export interface TracePage {
  items: TraceSummary[];
  count: number;
  has_more: boolean;
}

export interface TraceListQuery {
  tenant_id?: string;
  project_id?: string;
  from?: string;
  to?: string;
  limit: number;
  offset: number;
}
