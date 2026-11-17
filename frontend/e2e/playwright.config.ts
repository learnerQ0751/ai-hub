import { defineConfig, devices } from '@playwright/test';

// 5173/5174 被其他工作树的 Vite 占用（2026-11-17 实测），本工作树固定 5175。
const baseURL = process.env.QA_BASE_URL ?? 'http://127.0.0.1:5175';

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    viewport: { width: 1440, height: 900 },
  },
  // 本机 Chromium 下载极慢，先复用已安装的 Chrome（Chromium 内核）；
  // 装好 Playwright 自带 Chromium 后删掉 channel 即可切回。
  projects: [{ name: 'chrome', use: { ...devices['Desktop Chrome'], channel: 'chrome' } }],
});
