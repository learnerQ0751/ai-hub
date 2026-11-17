/** GET /v1/admin/usage 响应，字段与后端 UsageResDTO 一致（REQ-4.3）。 */
export interface UsageTotals {
  requests: number;
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  cost: number;
}

export interface UsageItem {
  date: string;
  tenant_id?: string | null;
  project_id?: string | null;
  model_alias?: string | null;
  key_id?: string | null;
  requests: number;
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  cost: number;
}

export interface UsageTrend {
  date: string;
  requests: number;
  total_tokens: number;
  cost: number;
}

export interface UsageResult {
  from: string;
  to: string;
  tenant_id?: string | null;
  project_id?: string | null;
  totals: UsageTotals;
  items: UsageItem[];
  trend: UsageTrend[];
  unpriced_models: string[];
}

export interface UsageQuery {
  from?: string;
  to?: string;
  tenant_id?: string;
  project_id?: string;
  model?: string;
  key_id?: string;
}

export const EMPTY_TOTALS: UsageTotals = {
  requests: 0,
  prompt_tokens: 0,
  completion_tokens: 0,
  total_tokens: 0,
  cost: 0,
};
