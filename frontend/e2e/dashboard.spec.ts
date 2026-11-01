import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page, persona: RegExp) {
  await page.goto('/login');
  await page.getByRole('button', { name: persona }).click();
  await expect(page.getByTestId('lis-value')).toBeVisible();
}

test.describe('executive dashboard', () => {
  test('shows the score, four dimensions, findings, penalties, recommendations and trends', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);

    await expect(page.getByTestId('lis-value')).toHaveText('67.00');
    await expect(page.getByText('Healthy').first()).toBeVisible();
    await expect(page.getByText(/not an individual performance rating/).first()).toBeVisible();

    for (const name of ['Team Health', 'Delivery Stability', 'Technical Risk', 'Leadership Load']) {
      await expect(page.getByRole('heading', { name, level: 3, exact: true })).toBeVisible();
    }

    const pattern = page.getByRole('article', { name: 'Team burnout risk' });
    await expect(pattern).toBeVisible();
    await expect(pattern).toContainText('Critical');
    await expect(pattern).toContainText('Confidence: Strong');

    const penalty = page.getByRole('article', { name: 'Burnout risk deduction' });
    await expect(penalty).toContainText('Applied');
    await expect(penalty).toContainText('12.00');

    await expect(page.getByRole('heading', { name: 'Review team capacity' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Historical trends' })).toBeVisible();
  });

  test('a severity count filters the patterns and the filter lives in the address', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await page.getByRole('button', { name: /High/ }).isDisabled();
    await page.getByRole('button', { name: /Critical/ }).click();
    await expect(page).toHaveURL(/severity=CRITICAL/);
    await expect(page.getByText(/Showing Critical severity only/)).toBeVisible();
    await page.getByRole('button', { name: 'Clear filter' }).click();
    await expect(page).not.toHaveURL(/severity=/);
  });

  test('evidence opens on demand for an analyst, with values', async ({ page }) => {
    await signIn(page, /^Analyst\b/);
    const pattern = page.getByRole('article', { name: 'Team burnout risk' });
    await pattern.getByRole('button', { name: 'Show evidence' }).click();
    await expect(pattern.getByText('Workload pressure', { exact: true })).toBeVisible();
    await expect(pattern.getByText('80.00')).toBeVisible();
  });

  test('a viewer sees the findings but not the values behind them', async ({ page }) => {
    await signIn(page, /^Viewer\b(?!,)/);
    const pattern = page.getByRole('article', { name: 'Team burnout risk' });
    await pattern.getByRole('button', { name: 'Show evidence' }).click();
    await expect(pattern.getByText(/Values are hidden for your role/)).toBeVisible();
    await expect(pattern.getByText('80.00')).toHaveCount(0);
    await expect(page.getByText('Question-level detail is hidden for your role.').first()).toBeVisible();
  });

  test('another tenant sees none of this data', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /^Administrator of another tenant/ }).click();
    await expect(page.getByText('No scored assessments yet')).toBeVisible();
    await expect(page.getByTestId('lis-value')).toHaveCount(0);
  });

  test('trends show exact values in an accessible data table', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await page.getByText('View the data').first().click();
    const table = page.getByRole('table', { name: 'Leadership Intelligence Score' });
    await expect(table).toBeVisible();
    // a header row plus one row per assessment (the page asks for the newest 12)
    expect(await table.getByRole('row').count()).toBeGreaterThanOrEqual(4);
  });
});
