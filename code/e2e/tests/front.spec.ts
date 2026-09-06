import { expect, test, type Locator, type Page } from '@playwright/test';

function flutterId(page: Page, id: string): Locator {
  return page.locator(`[flt-semantics-identifier="${id}"], [data-semantics-identifier="${id}"]`);
}

/**
 * Flutter Web の HTML レンダラでは semantics ノードと input/button が同時に存在する。
 * `.or()` は両方にマッチするため、strict mode 回避に `.first()` が必要。
 */
function firstMatch(a: Locator, b: Locator): Locator {
  return a.or(b).first();
}

/** CanvasKit では input 要素がなく、fill() がコントローラに届かない。 */
async function typeInto(page: Page, id: string, label: string, value: string) {
  const input = page.getByRole('textbox', { name: label, exact: true });
  if ((await input.count()) > 0) {
    await input.click();
  } else {
    await flutterId(page, id).click();
  }
  await page.keyboard.type(value, { delay: 20 });
}

test.describe('Flutter Web', () => {
  test('register reaches home', async ({ page }) => {
    const email = `pw-ui-${Date.now()}@example.com`;
    await page.goto('/register');
    await expect(page.getByRole('button', { name: 'ログインへ' })).toBeVisible({ timeout: 60_000 });

    await expect(
      firstMatch(flutterId(page, 'register-email'), page.getByRole('textbox', { name: 'メール' })),
    ).toBeVisible({ timeout: 60_000 });

    await typeInto(page, 'register-email', 'メール', email);
    await typeInto(page, 'register-password', 'パスワード', 'password1');
    await typeInto(page, 'register-password-confirm', 'パスワード確認', 'password1');

    const registered = page.waitForResponse(
      (res) => res.url().includes('/api/v1/auth/register') && res.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await firstMatch(
      flutterId(page, 'register-submit'),
      page.getByRole('button', { name: '登録' }),
    ).click();
    const res = await registered;
    expect(res.ok(), `register HTTP ${res.status()}`).toBeTruthy();

    await expect(page.getByRole('button', { name: '新しいゲーム' }).first()).toBeVisible({ timeout: 60_000 });
  });
});
