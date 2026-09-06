import { expect, test, type Page } from '@playwright/test';

async function fillLabeled(page: Page, label: string, value: string, exact = false) {
  const field = page.getByLabel(label, { exact });
  await field.click();
  await page.keyboard.type(value);
}

test.describe('Flutter Web', () => {
  test('register reaches home', async ({ page }) => {
    const email = `pw-ui-${Date.now()}@example.com`;
    await page.goto('/register');
    await expect(page.getByRole('button', { name: 'ログインへ' })).toBeVisible({ timeout: 60_000 });

    await fillLabeled(page, 'メール', email);
    await fillLabeled(page, 'パスワード', 'password1', true);
    await fillLabeled(page, 'パスワード確認', 'password1');
    await page.getByRole('button', { name: '登録' }).click();

    await expect(page.getByRole('button', { name: '新しいゲーム' })).toBeVisible({ timeout: 60_000 });
  });
});
