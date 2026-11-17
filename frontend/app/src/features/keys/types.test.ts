import { describe, expect, it } from 'vitest';
import { ADMIN_KEYS } from './fixtures';
import { filterKeys, formatBudget, formatLimits, keyStatus } from './types';

describe('filterKeys', () => {
  it('returns every key when no filter is set', () => {
    expect(filterKeys(ADMIN_KEYS, {})).toHaveLength(ADMIN_KEYS.length);
  });

  it('filters by status derived from blocked and expires_at', () => {
    const blocked = filterKeys(ADMIN_KEYS, { status: 'BLOCKED' });
    expect(blocked).toHaveLength(3);
    expect(blocked.every((key) => key.blocked)).toBe(true);

    const expired = filterKeys(ADMIN_KEYS, { status: 'EXPIRED' });
    expect(expired.map((key) => key.alias)).toEqual(['临时联调']);
  });

  it('filters by tenant and model together', () => {
    const result = filterKeys(ADMIN_KEYS, { tenantId: 'platform', model: 'text-embedding-v3' });
    expect(result.map((key) => key.alias)).toEqual(['研发-沙箱', '向量化任务']);
  });

  it('matches keyword against alias, hash and tenant', () => {
    expect(filterKeys(ADMIN_KEYS, { keyword: '客服' })[0]?.alias).toBe('客服-生产');
    expect(filterKeys(ADMIN_KEYS, { keyword: '9f2c41' })[0]?.alias).toBe('客服-生产');
    expect(filterKeys(ADMIN_KEYS, { keyword: 'partner-acme' })[0]?.alias).toBe('合作伙伴沙箱');
  });
});

describe('key display helpers', () => {
  it('derives status from blocked and expires_at', () => {
    expect(keyStatus(ADMIN_KEYS[0])).toBe('ACTIVE');
    expect(keyStatus(ADMIN_KEYS[3])).toBe('BLOCKED');
    expect(keyStatus(ADMIN_KEYS[ADMIN_KEYS.length - 1], Date.parse('2026-11-17T00:00:00Z'))).toBe(
      'EXPIRED',
    );
  });

  it('shows limits and budget with explicit unlimited fallbacks', () => {
    expect(formatLimits(ADMIN_KEYS[0])).toBe('RPM 600 · TPM 120,000');
    expect(formatLimits(ADMIN_KEYS[4])).toBe('RPM 不限 · TPM 不限');
    expect(formatBudget(ADMIN_KEYS[0])).toBe('128.42 / 300.00 / 30d');
    expect(formatBudget(ADMIN_KEYS[4])).toBe('341.90 / 不限');
  });
});
