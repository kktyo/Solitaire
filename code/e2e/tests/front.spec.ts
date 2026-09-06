import { expect, test, type Locator, type Page } from '@playwright/test';

function flutterId(page: Page, id: string): Locator {
  return page.locator(`[flt-semantics-identifier="${id}"]`);
}

/** CanvasKit では input 要素がなく、fill() がコントローラに届かない。 */
async function typeInto(page: Page, id: string, value: string) {
  const node = flutterId(page, id);
  await node.click();
  await page.keyboard.type(value, { delay: 20 });
}

test.describe('Flutter Web', () => {
  test('register reaches home', async ({ page }) => {
    const email = `pw-ui-${Date.now()}@example.com`;
    await page.goto('/register');
    await expect(page.getByRole('button', { name: 'ログインへ' })).toBeVisible({ timeout: 60_000 });

    await typeInto(page, 'register-email', email);
    await typeInto(page, 'register-password', 'password1');
    await typeInto(page, 'register-password-confirm', 'password1');

    const registered = page.waitForResponse(
      (res) => res.url().includes('/api/v1/auth/register') && res.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await page.getByRole('button', { name: '登録' }).click();
    const res = await registered;
    expect(res.ok(), `register HTTP ${res.status()}`).toBeTruthy();

    await expect(flutterId(page, 'home-new-game').or(page.getByRole('button', { name: '新しいゲーム' }))).toBeVisible({
      timeout: 60_000,
    });
  });
});
