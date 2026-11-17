import { auditActionLabel, formatAuditState } from '@/features/audit/types';
import { describe, expect, it } from 'vitest';

describe('audit 展示口径', () => {
  it('已知动作给中文标签，未知动作原样返回', () => {
    expect(auditActionLabel('PROMPT_PUBLISH')).toBe('发布 Prompt');
    expect(auditActionLabel('CUSTOM_ACTION')).toBe('CUSTOM_ACTION');
  });

  it('before/after 空对象与快照格式化', () => {
    expect(formatAuditState({})).toBe('{}');
    expect(formatAuditState({ version: 2 })).toBe('{\n  "version": 2\n}');
  });
});
