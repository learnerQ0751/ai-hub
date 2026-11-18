import { describe, expect, it } from 'vitest';
import { emptyModelForm, toAliasPayload, toModelFormValues } from './form';
import type { AdminModel } from './types';

const model: AdminModel = {
  alias: 'chat',
  tenant_id: 'platform',
  status: 'ACTIVE',
  description: '主对话别名',
  fallbacks: ['chat-backup'],
  input_price_per_million: null,
  output_price_per_million: null,
  created_at: '2026-11-18T02:00:00Z',
  updated_at: '2026-11-18T02:00:00Z',
  deployments: [
    {
      deployment_key: 'chat#0',
      api_base: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      upstream_model: 'qwen-plus',
      weight: 2,
      priority: 0,
      status: 'ACTIVE',
    },
  ],
};

describe('模型表单映射', () => {
  it('空表单默认启用、无 fallback、无部署，部署状态也为启用', () => {
    expect(emptyModelForm()).toEqual({
      alias: '',
      status: 'ACTIVE',
      deployment_status: 'ACTIVE',
      fallbacks: [],
    });
  });

  it('提交前去掉首尾空白，空字符串按未填写省略', () => {
    const payload = toAliasPayload({
      alias: '  chat  ',
      tenant_id: '   ',
      description: '  演示  ',
      status: 'ACTIVE',
      fallbacks: [' chat-backup ', ''],
    });

    expect(payload).toEqual({
      alias: 'chat',
      description: '演示',
      status: 'ACTIVE',
      fallbacks: ['chat-backup'],
    });
    expect('tenant_id' in payload).toBe(false);
  });

  it('只填了部分部署字段时不提交部署，避免半条实例', () => {
    const payload = toAliasPayload({
      alias: 'chat',
      status: 'ACTIVE',
      fallbacks: [],
      api_base: 'https://example.com/v1',
    });

    expect('deployments' in payload).toBe(false);
  });

  it('部署字段齐全时提交一条部署，api_key 留空则不下发（保留原密钥）', () => {
    const payload = toAliasPayload({
      alias: 'chat',
      status: 'ACTIVE',
      fallbacks: [],
      deployment_key: ' chat#0 ',
      api_base: ' https://example.com/v1 ',
      api_key: '   ',
      upstream_model: ' qwen-plus ',
      weight: 2,
      priority: 1,
      deployment_status: 'ACTIVE',
    });

    expect(payload.deployments).toEqual([
      {
        deployment_key: 'chat#0',
        api_base: 'https://example.com/v1',
        upstream_model: 'qwen-plus',
        weight: 2,
        priority: 1,
        status: 'ACTIVE',
      },
    ]);
  });

  it('编辑时用现有别名与首个部署预填，密钥不回显', () => {
    const values = toModelFormValues(model);

    expect(values).toEqual({
      alias: 'chat',
      tenant_id: 'platform',
      description: '主对话别名',
      status: 'ACTIVE',
      fallbacks: ['chat-backup'],
      deployment_key: 'chat#0',
      api_base: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      api_key: undefined,
      upstream_model: 'qwen-plus',
      weight: 2,
      priority: 0,
      deployment_status: 'ACTIVE',
    });
  });

  it('没有部署的别名预填时不带部署字段', () => {
    const values = toModelFormValues({ ...model, deployments: [] });

    expect('deployment_key' in values).toBe(false);
    expect('upstream_model' in values).toBe(false);
  });
});
