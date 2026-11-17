import { formatDateTime } from '@/features/shared/format';

export type ModelStatus = 'ACTIVE' | 'DISABLED';

/** 与后端 AdminDeploymentResDTO 一致；api-key 永不回显。 */
export interface AdminDeployment {
  deployment_key: string;
  api_base: string;
  upstream_model: string;
  weight: number;
  priority: number;
  status: string;
}

/**
 * GET /v1/admin/models 的列表项。
 * description、input_per_million、output_per_million、updated_at 属于 API-PATHS §7.2
 * 已定方向、尚未实现的字段；接入时按需容忍缺失。
 */
export interface AdminModel {
  alias: string;
  tenant_id?: string | null;
  status: ModelStatus;
  fallbacks: string[];
  deployments: AdminDeployment[];
  description?: string | null;
  input_price_per_million?: number | null;
  output_price_per_million?: number | null;
  created_at?: string | null;
  updated_at?: string | null;
}

export interface ModelFilters {
  keyword?: string;
  tenantId?: string;
  status?: ModelStatus;
}

export const MODEL_STATUS_LABEL: Record<ModelStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
};

export const MODEL_STATUS_DOT_COLOR: Record<ModelStatus, string> = {
  ACTIVE: '#16a34a',
  DISABLED: '#9ca3af',
};

export function filterModels(models: AdminModel[], filters: ModelFilters): AdminModel[] {
  const keyword = filters.keyword?.trim().toLowerCase();

  return models.filter((model) => {
    if (filters.tenantId && model.tenant_id !== filters.tenantId) {
      return false;
    }
    if (filters.status && model.status !== filters.status) {
      return false;
    }
    if (keyword) {
      const haystack = [model.alias, model.tenant_id, model.description]
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

export function formatPrice(model: AdminModel): string {
  const input = model.input_price_per_million;
  const output = model.output_price_per_million;
  if ((input === undefined || input === null) && (output === undefined || output === null)) {
    return '未配置';
  }
  const part = (value?: number | null) =>
    value === undefined || value === null ? '—' : value.toFixed(2);
  return `入 ${part(input)} / 出 ${part(output)}`;
}

export { formatDateTime };
