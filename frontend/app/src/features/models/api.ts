import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import { ADMIN_MODELS } from '@/features/models/fixtures';
import type { AdminModel } from '@/features/models/types';
import type { MockMode } from '@/features/shared/mock';
import { delay, pending } from '@/features/shared/mock';

/**
 * §7.2 字段（description、价格、更新时间、筛选参数）已由后端补齐（2026-11-17），
 * 数据源走真实 console 接口；mock 只保留四态演示。
 */
const USE_MOCK = false;

export interface ModelListQuery {
  tenant_id?: string;
  status?: 'ACTIVE' | 'DISABLED';
}

export async function fetchModels(
  mode: MockMode = 'ok',
  query: ModelListQuery = {},
): Promise<AdminModel[]> {
  if (mode === 'loading') {
    return pending<AdminModel[]>();
  }
  if (mode === 'error') {
    await delay();
    throw new Error('无法连接控制台服务（mock 故障）');
  }
  if (mode === 'empty') {
    await delay();
    return [];
  }
  if (!USE_MOCK) {
    return request<AdminModel[]>(withQuery(endpoints.models.list, { ...query }), { method: 'GET' });
  }
  await delay();
  return ADMIN_MODELS.map((model) => ({
    ...model,
    fallbacks: [...model.fallbacks],
    deployments: model.deployments.map((deployment) => ({ ...deployment })),
  }));
}
