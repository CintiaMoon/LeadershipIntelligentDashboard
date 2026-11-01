import { expect, test } from '@playwright/test';

// Phase 1 gate: sign in through the real OIDC authorization-code + PKCE flow and reach the dashboard.
test('a persona signs in and sees its organizations and roles', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);

  await page.getByRole('button', { name: /^Administrator Full access/ }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await expect(page.getByRole('banner').getByLabel('Organization')).toHaveValue('org-1');
  await expect(page.getByRole('banner').getByLabel('Organization').locator('option')).toHaveText(['org-1', 'org-2']);
  await expect(page.getByLabel('Your roles')).toContainText('Admin');
  await expect(page.getByRole('heading', { name: 'Leadership Intelligence', level: 1 })).toBeVisible();
});

test('a viewer restricted to one organization only gets that organization', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /^Viewer, one organization/ }).click();
  await expect(page.getByRole('banner').getByLabel('Organization').locator('option')).toHaveText(['org-1']);
  await expect(page.getByLabel('Your roles')).toContainText('Viewer');
});

test('signing out returns to the login page and protects the app again', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /^Owner\b/ }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});

test('an unknown address inside the app shows the not-found page', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /^Analyst\b/ }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  // a full reload keeps the session (it lives in sessionStorage) and lands inside the signed-in layout
  await page.goto('/nowhere');
  await expect(page.getByText('Page not found')).toBeVisible();
});
