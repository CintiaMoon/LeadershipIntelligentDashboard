import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { asPersona } from './api';

// Platform administration (decision D46): the platform administrator creates and lists tenants and sees no tenant data;
// the tenant roles can do neither. The tenants created here stay in the development database, so every run uses a new id.

async function signInAsPlatformAdmin(page: Page) {
  await page.goto('/login');
  await page.getByRole('button', { name: /^Platform administrator/ }).click();
  await expect(page.getByRole('heading', { name: 'Tenants', level: 1 })).toBeVisible();
}

async function signInAsTenantAdmin(page: Page) {
  await page.goto('/login');
  await page.getByRole('button', { name: /^Administrator Full access/ }).click();
  await expect(page.getByTestId('lis-value')).toBeVisible();
}

const newId = () => `e2e-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e4)}`;

async function expectNoViolations(page: Page, what: string) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  expect(summary, `${what} has accessibility violations`).toEqual([]);
}

test.describe('platform administration', () => {
  test('the platform administrator lands on the tenants and sees nothing that belongs to a tenant', async ({ page }) => {
    await signInAsPlatformAdmin(page);
    await expect(page).toHaveURL(/\/platform\/tenants$/);

    const nav = page.getByRole('navigation', { name: 'Main' });
    await expect(nav.getByRole('link', { name: 'Tenants' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Dashboard' })).toHaveCount(0);
    await expect(page.getByLabel('Organization')).toHaveCount(0);
    await expect(page.getByText('Platform administrator').first()).toBeVisible();

    const table = page.getByRole('table', { name: 'Tenants, oldest first' });
    await expect(table.getByRole('row', { name: /demo-tenant/ })).toBeVisible();
    await expect(table.getByRole('row', { name: /other-tenant/ })).toBeVisible();
    // no score, no organization, no assessment anywhere on the page
    await expect(page.getByTestId('lis-value')).toHaveCount(0);
  });

  test('creates a tenant, returns to the list and shows it there and through the API', async ({ page, request }) => {
    const id = newId();
    await signInAsPlatformAdmin(page);
    await page.getByRole('link', { name: 'Create tenant' }).first().click();
    await expect(page.getByRole('heading', { name: 'Create a tenant' })).toBeVisible();
    await expect(page.getByText(/cannot be changed later/)).toBeVisible();

    await page.getByLabel('Tenant id').fill(id);
    await page.getByLabel('Name').fill('End to end tenant');
    await page.getByRole('button', { name: 'Create tenant' }).click();

    await expect(page).toHaveURL(/\/platform\/tenants$/);
    await expect(page.getByRole('status').filter({ hasText: 'was created' })).toContainText(id);
    await expect(page.getByRole('row', { name: new RegExp(id) })).toContainText('End to end tenant');

    const admin = await asPersona(request, 'platform-admin');
    const list = await admin.json<{ items: { id: string; name: string }[] }>('/platform/tenants');
    expect(list.items.find((t) => t.id === id)?.name).toBe('End to end tenant');

    // the list can be filtered
    await page.getByRole('searchbox', { name: 'Filter' }).fill(id);
    await expect(page.getByRole('row')).toHaveCount(2); // header and the one match
  });

  test('refuses an id that is taken or not allowed, on the field, without leaving the form', async ({ page }) => {
    await signInAsPlatformAdmin(page);
    await page.goto('/platform/tenants/new');

    await page.getByLabel('Tenant id').fill('demo-tenant');
    await page.getByLabel('Name').fill('Duplicate');
    await page.getByRole('button', { name: 'Create tenant' }).click();
    await expect(page.getByText(/A tenant with this id already exists/)).toBeVisible();
    await expect(page.getByLabel('Tenant id')).toHaveAttribute('aria-invalid', 'true');
    await expect(page).toHaveURL(/\/platform\/tenants\/new$/);

    await page.getByLabel('Tenant id').fill('Not Allowed');
    await page.getByRole('button', { name: 'Create tenant' }).click();
    await expect(page.getByText(/Use 2 to 63 lowercase letters, digits or hyphens/)).toBeVisible();
    await expect(page).toHaveURL(/\/platform\/tenants\/new$/);
  });

  test('a tenant administrator has no way in: no link, no page and no data from the API', async ({ page, request }) => {
    await signInAsTenantAdmin(page);
    await expect(page.getByRole('link', { name: 'Tenants' })).toHaveCount(0);

    await page.goto('/platform/tenants');
    await expect(page.getByRole('heading', { name: 'Only for platform administrators' })).toBeVisible();
    await page.goto('/platform/tenants/new');
    await expect(page.getByRole('heading', { name: 'Only for platform administrators' })).toBeVisible();
    await expect(page.getByLabel('Tenant id')).toHaveCount(0);

    for (const persona of ['admin', 'owner', 'viewer', 'other-admin']) {
      const api = await asPersona(request, persona);
      expect((await api.get('/platform/tenants')).status(), `${persona} lists tenants`).toBe(403);
      expect((await api.post('/platform/tenants', { id: newId(), name: 'Sneaky' })).status(), `${persona} creates a tenant`).toBe(403);
    }
  });

  test('the platform administrator can read nothing that belongs to a tenant', async ({ request }) => {
    const root = await asPersona(request, 'platform-admin');
    for (const path of ['/scoring-runs', '/organizations/org-1/dashboard', '/assessments']) {
      expect((await root.get(path)).status(), path).toBe(403);
    }
    const anonymous = await request.get('http://localhost:8080/api/v1/platform/tenants');
    expect(anonymous.status()).toBe(401);
  });

  test('the new screens have no automated WCAG 2.2 AA violations and fit a phone', async ({ page }) => {
    await signInAsPlatformAdmin(page);
    await expectNoViolations(page, 'tenant list');

    await page.goto('/platform/tenants/new');
    await expect(page.getByRole('heading', { name: 'Create a tenant' })).toBeVisible();
    await expectNoViolations(page, 'create tenant form');
    await page.getByRole('button', { name: 'Create tenant' }).click();
    await expect(page.getByText('Enter a name.')).toBeVisible();
    await expectNoViolations(page, 'create tenant form with errors');

    await page.setViewportSize({ width: 390, height: 800 });
    for (const path of ['/platform/tenants', '/platform/tenants/new']) {
      await page.goto(path);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${path} scrolls horizontally`).toBeLessThanOrEqual(0);
    }
  });

  test('the not-allowed page for a tenant role is accessible too', async ({ page }) => {
    await signInAsTenantAdmin(page);
    await page.goto('/platform/tenants');
    await expect(page.getByRole('heading', { name: 'Only for platform administrators' })).toBeVisible();
    await expectNoViolations(page, 'not allowed');
  });
});
