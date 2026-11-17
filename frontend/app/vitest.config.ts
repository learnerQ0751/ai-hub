import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
    // antd 整页在 jsdom 里渲染较慢；本机常与后端构建并行，放宽默认 5s 超时。
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});
