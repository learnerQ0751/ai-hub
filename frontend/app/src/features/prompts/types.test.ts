import { PROMPT_STATUS_LABEL, promptStatusOf } from '@/features/prompts/types';
import { describe, expect, it } from 'vitest';

describe('prompts 状态口径', () => {
  it('只认后端三种状态，未知值回落到草稿', () => {
    expect(promptStatusOf('PUBLISHED')).toBe('PUBLISHED');
    expect(promptStatusOf('ARCHIVED')).toBe('ARCHIVED');
    expect(promptStatusOf('DRAFT')).toBe('DRAFT');
    expect(promptStatusOf('WHATEVER')).toBe('DRAFT');
    expect(PROMPT_STATUS_LABEL.PUBLISHED).toBe('已发布');
  });
});
