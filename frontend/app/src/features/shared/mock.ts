/** 控制台列表页共用的四态演示模式；mock 只替换数据源，不改变响应结构。 */
export type MockMode = 'ok' | 'loading' | 'empty' | 'error';

export const MOCK_MODE_OPTIONS: { value: MockMode; label: string }[] = [
  { value: 'ok', label: '正常' },
  { value: 'loading', label: '加载中' },
  { value: 'empty', label: '空数据' },
  { value: 'error', label: '加载失败' },
];

export const delay = (ms = 450): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

export const pending = <T>(): Promise<T> => new Promise<T>(() => {});
