import { endpoints, withQuery } from '@/api/endpoints';
import { ApiError, request } from '@/api/http';
import { ADMIN_MODELS } from '@/features/models/fixtures';
import type { AliasUpsertPayload } from '@/features/models/form';
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

/** 409 conflict_error 转成页面能直接展示的中文结论，其余错误原样抛出。 */
function rethrowConflict(cause: unknown, alias: string): never {
  if (cause instanceof ApiError && cause.status === 409) {
    throw new ApiError(409, `同名模型已存在：${alias}`);
  }
  throw cause;
}

export async function createModel(payload: AliasUpsertPayload): Promise<AdminModel> {
  try {
    return await request<AdminModel>(endpoints.models.create, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  } catch (cause) {
    rethrowConflict(cause, payload.alias);
  }
}

export async function updateModel(alias: string, payload: AliasUpsertPayload): Promise<AdminModel> {
  try {
    return await request<AdminModel>(endpoints.models.update(alias), {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  } catch (cause) {
    rethrowConflict(cause, alias);
  }
}

export async function deleteModel(alias: string): Promise<void> {
  await request<void>(endpoints.models.remove(alias), { method: 'DELETE' });
}
