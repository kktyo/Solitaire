import { test, expect } from '@playwright/test';

test.describe('API', () => {
  test('health is UP', async ({ request }) => {
    const res = await request.get('/api/v1/health');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.status).toBe('UP');
  });

  test('register login and reject unauthenticated game', async ({ request }) => {
    const email = `pw-${Date.now()}@example.com`;
    const password = 'password1';

    const registered = await request.post('/api/v1/auth/register', {
      data: { email, password },
    });
    expect(registered.status()).toBe(201);
    const tokens = await registered.json();
    expect(tokens.accessToken).toBeTruthy();

    const duplicate = await request.post('/api/v1/auth/register', {
      data: { email, password },
    });
    expect(duplicate.status()).toBe(400);
    expect((await duplicate.json()).code).toBe('EMAIL_TAKEN');

    const loggedIn = await request.post('/api/v1/auth/login', {
      data: { email, password },
    });
    expect(loggedIn.ok()).toBeTruthy();
    const access = (await loggedIn.json()).accessToken as string;

    const anon = await request.get('/api/v1/games/current');
    expect(anon.status()).toBe(401);

    const current = await request.get('/api/v1/games/current', {
      headers: { Authorization: `Bearer ${access}` },
    });
    expect(current.status()).toBe(404);
  });
});
