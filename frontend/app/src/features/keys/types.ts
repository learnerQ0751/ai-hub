import { formatAmount, formatDateTime, formatNumber } from '@/features/shared/format';

export type KeyStatus = 'ACTIVE' | 'BLOCKED' | 'EXPIRED';

/**
 * GET /v1/admin/keys 的列表项。
 * 基础字段与后端 AdminKeyResDTO 一致；alias、metadata、rpm_limit、tpm_limit、
 * budget_duration 是 API-PATHS §7.2 已定、尚未实现的字段，接入时按需容忍缺失。
 */
export interface AdminKey {
  key_hash: string;
  alias?: string | null;
  models: string[];
  metadata?: Record<string, unknown> | null;
  rpm_limit?: number | null;
  tpm_limit?: number | null;
  spend: number;
  max_budget?: number | null;
  budget_duration?: string | null;
  blocked: boolean;
  tenant_id?: string | null;
  project_id?: string | null;
  expires_at?: string | null;
  created_at: string;
}

/** 与后端 AdminKeyPageResDTO 一致。 */
export interface AdminKeyPage {
  items: AdminKey[];
  count: number;
  has_more: boolean;
}

export interface KeyListQuery {
  tenant_id?: string;
  project_id?: string;
  /** §4.7 冻结值：active / blocked / expired。 */
  status?: 'active' | 'blocked' | 'expired';
  model?: string;
  limit?: number;
  offset?: number;
}

/** POST /v1/admin/keys 请求体；字段名对齐后端 KeyGenerateReqDTO。 */
export interface GenerateKeyInput {
  alias?: string;
  tenant_id?: string;
  project_id?: string;
  models?: string[];
  duration?: string;
  rpm_limit?: number;
  tpm_limit?: number;
  max_budget?: number;
  budget_duration?: string;
}

/** POST /v1/admin/keys 响应；`key` 是唯一一次返回的明文。 */
export interface GeneratedKey {
  key: string;
  models: string[];
  spend: number;
  metadata?: Record<string, unknown> | null;
  expires?: string | null;
  max_budget?: number | null;
  budget_duration?: string | null;
  rpm_limit?: number | null;
  tpm_limit?: number | null;
  blocked: boolean;
  tenant_id?: string | null;
  project_id?: string | null;
  alias?: string | null;
}

export interface KeyFilters {
  keyword?: string;
  tenantId?: string;
  status?: KeyStatus;
  model?: string;
}

export const KEY_STATUS_LABEL: Record<KeyStatus, string> = {
  ACTIVE: '正常',
  BLOCKED: '已封禁',
  EXPIRED: '已过期',
};

export const KEY_STATUS_DOT_COLOR: Record<KeyStatus, string> = {
  ACTIVE: '#16a34a',
  BLOCKED: '#dc2626',
  EXPIRED: '#9ca3af',
};

/** 与后端筛选口径一致：blocked 优先，其次 expired，其余为 active。 */
export function keyStatus(key: AdminKey, now: number = Date.now()): KeyStatus {
  if (key.blocked) {
    return 'BLOCKED';
  }
  if (key.expires_at && new Date(key.expires_at).getTime() <= now) {
    return 'EXPIRED';
  }
  return 'ACTIVE';
}

export function keyName(key: AdminKey): string {
  return key.alias ?? key.key_hash;
}

export function filterKeys(keys: AdminKey[], filters: KeyFilters): AdminKey[] {
  const keyword = filters.keyword?.trim().toLowerCase();

  return keys.filter((key) => {
    if (filters.tenantId && key.tenant_id !== filters.tenantId) {
      return false;
    }
    if (filters.status && keyStatus(key) !== filters.status) {
      return false;
    }
    if (filters.model && !key.models.includes(filters.model)) {
      return false;
    }
    if (keyword) {
      const haystack = [key.alias, key.key_hash, key.tenant_id, key.project_id, ...key.models]
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

export function formatLimits(key: AdminKey): string {
  const rpm =
    key.rpm_limit === undefined || key.rpm_limit === null ? '不限' : formatNumber(key.rpm_limit);
  const tpm =
    key.tpm_limit === undefined || key.tpm_limit === null ? '不限' : formatNumber(key.tpm_limit);
  return `RPM ${rpm} · TPM ${tpm}`;
}

export function formatBudget(key: AdminKey): string {
  const budget =
    key.max_budget === undefined || key.max_budget === null ? '不限' : formatAmount(key.max_budget);
  const cycle = key.budget_duration ? ` / ${key.budget_duration}` : '';
  return `${formatAmount(key.spend)} / ${budget}${cycle}`;
}

export { formatDateTime };
