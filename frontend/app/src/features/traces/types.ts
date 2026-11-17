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

/** GET /v1/admin/traces 的列表行（§4.6 / §7.1 冻结口径）。 */
export interface TraceSummary {
  trace_id: string;
  tenant_id?: string | null;
  project_id?: string | null;
  key_id?: string | null;
  started_at?: string | null;
  finished_at?: string | null;
  event_count: number;
  /** 分段最大状态码；0 表示未记录。 */
  status: number;
}

export interface TracePage {
  items: TraceSummary[];
  count: number;
  has_more: boolean;
}

export interface TraceListQuery {
  tenant_id?: string;
  project_id?: string;
  key_id?: string;
  status?: number;
  from?: string;
  to?: string;
  limit: number;
  offset: number;
}
