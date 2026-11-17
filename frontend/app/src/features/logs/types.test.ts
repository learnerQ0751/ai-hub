import { formatCost, formatLatency, logStatusTone } from '@/features/logs/types';
import { describe, expect, it } from 'vitest';

describe('logs 展示口径', () => {
  it('状态码分档：2xx 成功、429 限流、其余错误', () => {
    expect(logStatusTone(200)).toBe('success');
    expect(logStatusTone(201)).toBe('success');
    expect(logStatusTone(429)).toBe('warning');
    expect(logStatusTone(500)).toBe('error');
    expect(logStatusTone(401)).toBe('error');
  });

  it('费用与延迟格式', () => {
    expect(formatCost(null)).toBe('—');
    expect(formatCost(0.0123)).toBe('0.0123');
    expect(formatLatency(320)).toBe('320 ms');
    expect(formatLatency(1500)).toBe('1.50 s');
  });
});
