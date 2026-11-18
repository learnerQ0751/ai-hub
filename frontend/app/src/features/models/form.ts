import type { AdminModel, ModelStatus } from '@/features/models/types';

/**
 * 表单与后端 AliasUpsertReqDTO 的映射。
 * 价格（input_price/output_price）不是别名可写字段：由主部署上游模型的价格配置推导，
 * 所以表单不出现价格输入，只读展示 formatPrice 的结果。
 */
export interface ModelDeploymentPayload {
  deployment_key?: string;
  api_base: string;
  api_key?: string;
  upstream_model: string;
  weight: number;
  priority: number;
  status: ModelStatus;
}

export interface AliasUpsertPayload {
  alias: string;
  tenant_id?: string;
  description?: string;
  status: ModelStatus;
  fallbacks: string[];
  deployments?: ModelDeploymentPayload[];
}

export interface ModelFormValues {
  alias: string;
  tenant_id?: string;
  description?: string;
  status: ModelStatus;
  fallbacks?: string[];
  deployment_key?: string;
  api_base?: string;
  api_key?: string;
  upstream_model?: string;
  weight?: number;
  priority?: number;
  deployment_status?: ModelStatus;
}

export function emptyModelForm(): ModelFormValues {
  return { alias: '', status: 'ACTIVE', deployment_status: 'ACTIVE', fallbacks: [] };
}

function clean(value?: string | null): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function toDeploymentPayload(values: ModelFormValues): ModelDeploymentPayload | null {
  const apiBase = clean(values.api_base);
  const upstreamModel = clean(values.upstream_model);
  if (!apiBase || !upstreamModel) {
    return null;
  }
  const payload: ModelDeploymentPayload = {
    api_base: apiBase,
    upstream_model: upstreamModel,
    weight: values.weight && values.weight > 0 ? values.weight : 1,
    priority: values.priority !== undefined && values.priority >= 0 ? values.priority : 0,
    status: values.deployment_status ?? 'ACTIVE',
  };
  const deploymentKey = clean(values.deployment_key);
  if (deploymentKey) {
    payload.deployment_key = deploymentKey;
  }
  // 留空表示沿用已保存的上游密钥；后端对空 api_key 不做覆盖。
  const apiKey = clean(values.api_key);
  if (apiKey) {
    payload.api_key = apiKey;
  }
  return payload;
}

export function toAliasPayload(values: ModelFormValues): AliasUpsertPayload {
  const payload: AliasUpsertPayload = {
    alias: values.alias.trim(),
    status: values.status ?? 'ACTIVE',
    fallbacks: (values.fallbacks ?? []).map((item) => item.trim()).filter(Boolean),
  };
  const tenantId = clean(values.tenant_id);
  if (tenantId) {
    payload.tenant_id = tenantId;
  }
  const description = clean(values.description);
  if (description) {
    payload.description = description;
  }
  const deployment = toDeploymentPayload(values);
  if (deployment) {
    payload.deployments = [deployment];
  }
  return payload;
}

export function toModelFormValues(model: AdminModel, deploymentKey?: string): ModelFormValues {
  const values: ModelFormValues = {
    alias: model.alias,
    status: model.status,
    fallbacks: [...model.fallbacks],
    api_key: undefined,
  };
  if (model.tenant_id) {
    values.tenant_id = model.tenant_id;
  }
  if (model.description) {
    values.description = model.description;
  }
  const deployment = deploymentKey
    ? model.deployments.find((item) => item.deployment_key === deploymentKey)
    : model.deployments[0];
  if (deployment) {
    values.deployment_key = deployment.deployment_key;
    values.api_base = deployment.api_base;
    values.upstream_model = deployment.upstream_model;
    values.weight = deployment.weight;
    values.priority = deployment.priority;
    values.deployment_status = deployment.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE';
  }
  return values;
}
