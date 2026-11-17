import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import type { TraceDetail, TraceListQuery, TracePage } from '@/features/traces/types';

export async function fetchTraces(query: TraceListQuery): Promise<TracePage> {
  return request<TracePage>(withQuery(endpoints.traces.list, { ...query }), { method: 'GET' });
}

export async function fetchTrace(traceId: string): Promise<TraceDetail> {
  return request<TraceDetail>(endpoints.traces.detail(traceId), { method: 'GET' });
}
