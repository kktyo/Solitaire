import { test, expect } from '@playwright/test';

test.describe('Flutter Web', () => {
  test('register reaches home', async ({ page }) => {
    const email = `pw-ui-${Date.now()}@example.com`;
    await page.goto('/login');
    await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible({ timeout: 60_000 });

    await page.getByRole('button', { name: '登録' }).click();
    await expect(page.getByText('パスワード確認')).toBeVisible();

    await page.getByLabel('メール').fill(email);
    await page.getByLabel('パスワード', { exact: true }).fill('password1');
    await page.getByLabel('パスワード確認').fill('password1');
    await page.getByRole('button', { name: '登録' }).click();

    await expect(page.getByRole('button', { name: '新しいゲーム' })).toBeVisible({ timeout: 60_000 });
  });
});
