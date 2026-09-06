import { expect, test, type Page } from '@playwright/test';

/** Flutter Web の TextField は通常 DOM input ではなく、フォーカス時だけ .flt-text-editing が出る。 */
async function fillFlutterField(page: Page, name: string, value: string, exact = false) {
  const field = page.getByRole('textbox', { name, exact }).or(page.getByLabel(name, { exact }));
  await field.first().click();
  const editing = page.locator('input.flt-text-editing, textarea.flt-text-editing');
  try {
    await editing.first().waitFor({ state: 'attached', timeout: 8_000 });
    await editing.first().fill(value);
  } catch {
    await page.keyboard.press('Control+A');
    await page.keyboard.insertText(value);
  }
  await page.keyboard.press('Tab');
}

test.describe('Flutter Web', () => {
  test('register reaches home', async ({ page }) => {
    const email = `pw-ui-${Date.now()}@example.com`;
    await page.goto('/register');
    await expect(page.getByRole('button', { name: 'ログインへ' })).toBeVisible({ timeout: 60_000 });

    await fillFlutterField(page, 'メール', email);
    await fillFlutterField(page, 'パスワード', 'password1', true);
    await fillFlutterField(page, 'パスワード確認', 'password1');

    const registered = page.waitForResponse(
      (r) => r.url().includes('/api/v1/auth/register') && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await page.getByRole('button', { name: '登録' }).click();
    const res = await registered;
    expect(res.status(), await res.text()).toBe(201);
    await page.waitForURL(/\/home/, { timeout: 20_000 });

    await expect(page.getByText('新しいゲーム')).toBeVisible({ timeout: 60_000 });
  });
});
