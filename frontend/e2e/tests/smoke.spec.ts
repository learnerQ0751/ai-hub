import { expect, test } from '@playwright/test';

// 阻塞（2026-11-17 实测）：main 89f334b 的 src/router/index.tsx 静态引用了
// src/pages/logs/LogsPage.tsx，但该文件从未提交，整个 SPA 无法加载。
// 文件补齐后把 describe.fixme 改回 describe 即可启用。
test.describe.fixme('阻塞：main 缺 pages/logs/LogsPage.tsx', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.locator('#root > *').first().waitFor();
    await expect(page.locator('vite-error-overlay')).toHaveCount(0);
  });

  test('登录页可打开：标题正确并截图', async ({ page }) => {
    await expect(page).toHaveTitle('ai-hub 控制台');
    await expect(page.getByRole('heading', { name: 'ai-hub 控制台' })).toBeVisible();
    await page.screenshot({ path: 'artifacts/login-page.png', fullPage: true });
  });

  test('mock 管理员登录后进入概览', async ({ page }) => {
    await page.getByLabel('用户名').fill('admin');
    await page.getByLabel('密码').fill('admin123');
    await page.getByRole('button', { name: '登录' }).click();
    await expect(page).toHaveURL(/\/overview$/);
    await page.screenshot({ path: 'artifacts/overview-after-login.png', fullPage: true });
  });
});
