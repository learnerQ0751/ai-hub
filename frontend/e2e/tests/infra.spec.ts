import { expect, test } from '@playwright/test';

// 只证明浏览器 → 本工作树 Vite → HTML 这条链路可用，不代表业务页面通过。
test('Playwright 链路：打开开发服务器根路径并截图', async ({ page }) => {
  const resp = await page.goto('/');
  expect(resp?.status()).toBe(200);
  await expect(page).toHaveTitle('ai-hub 控制台');
  await page.screenshot({ path: 'artifacts/infra-landing.png', fullPage: true });
});
