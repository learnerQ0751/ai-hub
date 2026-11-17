import type { MockMode } from '@/features/shared/mock';

const MODES: MockMode[] = ['ok', 'loading', 'empty', 'error'];

/** Board 预览用：只决定四态展示，不影响真实数据来源。 */
export function parsePreviewMode(value: string | null): MockMode {
  return MODES.includes(value as MockMode) ? (value as MockMode) : 'ok';
}
