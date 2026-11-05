import { expect, test, pickPersona } from './fixtures';
import { asPersona } from './api';

const APP = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

test.describe('hardening', () => {
  test('the web server sends the security headers, and never its version', async ({ request }) => {
    const response = await request.get(`${APP}/`);
    const h = response.headers();
    expect(h['content-security-policy']).toContain("default-src 'self'");
    expect(h['content-security-policy']).toContain("script-src 'self'");
    expect(h['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(h['content-security-policy']).toContain("object-src 'none'");
    expect(h['content-security-policy']).toMatch(/connect-src 'self';/);
    expect(h['x-content-type-options']).toBe('nosniff');
    expect(h['x-frame-options']).toBe('DENY');
    expect(h['referrer-policy']).toBe('no-referrer');
    expect(h['permissions-policy']).toContain('camera=()');
    expect(h['server'] ?? '').not.toMatch(/\d+\.\d+/);
  });

  test('the application shell is never cached, built assets are cached for good', async ({ request, page }) => {
    const shell = await request.get(`${APP}/dashboard`);
    expect(shell.headers()['cache-control']).toMatch(/no-cache|no-store|max-age=0/);
    await page.goto(`${APP}/login`);
    const asset = await page.evaluate(() => (document.querySelector('script[src*="/assets/"]') as HTMLScriptElement).src);
    const cached = await request.get(asset);
    expect(cached.headers()['cache-control']).toMatch(/max-age=31536000/);
    // security headers survive on proxied API responses too
    expect((await request.get(`${APP}/api/v1/scoring-runs`)).headers()['x-content-type-options']).toBe('nosniff');
  });

  test('a full journey runs without a single content-security-policy violation or console error', async ({ page }) => {
    const problems: string[] = [];
    page.on('console', (m) => {
      // the probe for a session before anyone has signed in is answered 401 by design; the browser logs it as a failed resource
      if (m.type() === 'error' && !/status of 401/.test(m.text())) problems.push(m.text());
    });
    page.on('pageerror', (e) => problems.push(e.message));
    await page.goto('/login');
    await pickPersona(page, /^Administrator Full access/);
    await expect(page.getByTestId('lis-value')).toBeVisible();
    await page.getByRole('link', { name: 'How was this calculated?' }).click();
    await expect(page.getByRole('heading', { name: 'Final LIS' })).toBeVisible();
    await page.goBack();
    await page.getByRole('link', { name: /Detailed analysis: Team Health/ }).click();
    await expect(page.getByTestId('adjusted-score')).toBeVisible();
    expect(problems).toEqual([]);
  });

  test('the session is an HttpOnly cookie, and nothing of it or of a result is kept in browser storage', async ({ page, context }) => {
    await page.goto('/login');
    await pickPersona(page, /^Administrator Full access/);
    await expect(page.getByTestId('lis-value')).toBeVisible();
    const stored = await page.evaluate(() => ({
      local: Object.keys(window.localStorage),
      session: Object.keys(window.sessionStorage),
      cookies: document.cookie,
    }));
    expect(stored.local).toEqual([]);
    // script cannot read the session cookie (HttpOnly); only the CSRF token, which is meant to be read, is visible
    expect(stored.cookies).not.toContain('LIS_SESSION');
    const cookies = await context.cookies();
    const session = cookies.find((c) => c.name === 'LIS_SESSION');
    expect(session?.httpOnly).toBe(true);
    expect(session?.sameSite).toBe('Lax');
    // no scoring result and no credential is kept client-side: only small preferences
    const values = await page.evaluate(() => JSON.stringify({ ...window.sessionStorage, ...window.localStorage }));
    expect(values).not.toContain('leadershipIntelligenceScore');
    expect(values).not.toContain('only one key person');
    expect(values).not.toContain('demo-password');
  });

  test('the API never describes its internals to a caller who did something wrong', async ({ request }) => {
    const admin = await asPersona(request, 'admin');
    for (const [path, status] of [
      ['/scoring-runs/not-a-uuid', 400],
      ['/scoring-runs/00000000-0000-0000-0000-000000000000', 404],
      ['/scoring-runs?limit=0', 400],
      ['/assessments/asm-does-not-exist', 404],
    ] as const) {
      const response = await admin.get(path);
      expect(response.status(), path).toBe(status);
      const text = await response.text();
      expect(text, path).not.toMatch(/Exception|\bat com\.|org\.springframework|jdbc|select |postgres/i);
      expect(JSON.parse(text)).toHaveProperty('code');
    }
    const tampered = await request.get('http://localhost:8080/api/v1/scoring-runs', { headers: { Authorization: 'Bearer not.a.token' } });
    expect(tampered.status()).toBe(401);
  });

  test('responses to authenticated calls are not cacheable by shared caches', async ({ request }) => {
    const admin = await asPersona(request, 'admin');
    const response = await admin.get('/scoring-runs?limit=1');
    expect(response.headers()['cache-control']).toMatch(/no-store|no-cache|private/);
    expect(response.headers()['x-content-type-options']).toBe('nosniff');
  });
});
