import { expect, test, pickPersona } from './fixtures';

// Phase 1 gate: sign in with the email and password of a demo account and reach the dashboard.
test('a persona signs in and sees its organizations and roles', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);

  await pickPersona(page, /^Administrator Full access/);
  await expect(page).toHaveURL(/\/dashboard$/);

  await expect(page.getByRole('banner').getByLabel('Organization')).toHaveValue('org-1');
  await expect(page.getByRole('banner').getByLabel('Organization').locator('option')).toHaveText(['org-1', 'org-2']);
  await expect(page.getByLabel('Your roles')).toContainText('Admin');
  await expect(page.getByRole('heading', { name: 'Leadership Intelligence', level: 1 })).toBeVisible();
});

test('a viewer restricted to one organization only gets that organization', async ({ page }) => {
  await page.goto('/login');
  await pickPersona(page, /^Viewer, one organization/);
  await expect(page.getByRole('banner').getByLabel('Organization').locator('option')).toHaveText(['org-1']);
  await expect(page.getByLabel('Your roles')).toContainText('Viewer');
});

test('signing out returns to the login page and protects the app again', async ({ page }) => {
  await page.goto('/login');
  await pickPersona(page, /^Owner\b/);
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login(\?|$)/);
});

test('an unknown address inside the app shows the not-found page', async ({ page }) => {
  await page.goto('/login');
  await pickPersona(page, /^Analyst\b/);
  await expect(page).toHaveURL(/\/dashboard$/);
  // a full reload keeps the session (an HttpOnly cookie) and lands inside the signed-in layout
  await page.goto('/nowhere');
  await expect(page.getByText('Page not found')).toBeVisible();
});
