import { describe, expect, it } from 'vitest';
import { ADMIN_MODELS } from './fixtures';
import { filterModels, formatPrice } from './types';

describe('filterModels', () => {
  it('returns every model when no filter is set', () => {
    expect(filterModels(ADMIN_MODELS, {})).toHaveLength(ADMIN_MODELS.length);
  });

  it('filters by tenant and status together', () => {
    const result = filterModels(ADMIN_MODELS, { tenantId: 'research', status: 'ACTIVE' });
    expect(result.map((model) => model.alias)).toEqual(['deepseek-reasoner', 'claude-sonnet-4-6']);
  });

  it('matches keyword against alias, tenant and description', () => {
    expect(filterModels(ADMIN_MODELS, { keyword: 'embedding' })[0]?.alias).toBe(
      'text-embedding-v3',
    );
    expect(filterModels(ADMIN_MODELS, { keyword: 'legacy' })[0]?.alias).toBe('upstream-chat');
    expect(filterModels(ADMIN_MODELS, { keyword: '长上下文' })[0]?.alias).toBe('claude-sonnet-4-6');
  });
});

describe('formatPrice', () => {
  it('prints input and output prices per million tokens', () => {
    expect(formatPrice(ADMIN_MODELS[0])).toBe('入 0.27 / 出 1.10');
  });

  it('handles missing and partial prices', () => {
    expect(formatPrice(ADMIN_MODELS[2])).toBe('入 0.50 / 出 —');
    expect(
      formatPrice({
        ...ADMIN_MODELS[0],
        input_price_per_million: undefined,
        output_price_per_million: undefined,
      }),
    ).toBe('未配置');
  });
});
