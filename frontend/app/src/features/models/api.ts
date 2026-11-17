import { endpoints } from '@/api/endpoints';
import { request } from '@/api/http';
import { ADMIN_MODELS } from '@/features/models/fixtures';
import type { AdminModel } from '@/features/models/types';
import type { MockMode } from '@/features/shared/mock';
import { delay, pending } from '@/features/shared/mock';

/**
 * 后端 §7.2 字段（description、价格、更新时间、筛选参数）尚未实现，
 * 暂时由 fixtures 顶替数据源；置为 false 即走真实 console 接口，页面与类型不用改。
 */
const USE_MOCK = true;

export async function fetchModels(mode: MockMode = 'ok'): Promise<AdminModel[]> {
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
    return request<AdminModel[]>(endpoints.models.list, { method: 'GET' });
  }
  await delay();
  return ADMIN_MODELS.map((model) => ({
    ...model,
    fallbacks: [...model.fallbacks],
    deployments: model.deployments.map((deployment) => ({ ...deployment })),
  }));
}
