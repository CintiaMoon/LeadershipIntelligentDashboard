import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page, persona: RegExp) {
  await page.goto('/login');
  await page.getByRole('button', { name: persona }).click();
  await expect(page.getByTestId('lis-value')).toBeVisible();
}

test.describe('explainability', () => {
  test('the explanation walks from the inputs to the same final score the dashboard shows', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    const score = (await page.getByTestId('lis-value').textContent()) ?? '';
    await page.getByRole('link', { name: 'How was this calculated?' }).click();

    await expect(page.getByRole('heading', { name: 'How the Leadership Intelligence Score was calculated' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Calculation steps' }).getByRole('link')).toHaveCount(8);

    const final = page.getByRole('heading', { name: 'Final LIS' }).locator('xpath=ancestor::details');
    await expect(final.getByRole('img', { name: `Final score ${score}` })).toBeVisible();

    await page.getByRole('heading', { name: 'Normalization' }).click();
    await expect(page.getByRole('table', { name: 'Normalized answers' })).toBeVisible();
    await page.getByRole('heading', { name: 'Penalties', level: 2 }).click();
    await expect(page.getByRole('table', { name: 'Penalties' }).getByRole('row', { name: /Burnout risk deduction/ })).toContainText('Applied');
  });

  test('a dimension opens with its contributions and, for an analyst, the source answer', async ({ page }) => {
    await signIn(page, /^Analyst\b/);
    await page.getByRole('link', { name: 'Detailed analysis: Team Health' }).click();
    await expect(page.getByRole('heading', { name: 'Team Health', level: 1 })).toBeVisible();
    const table = page.getByRole('table', { name: 'Contributions to Team Health' });
    await expect(table).toBeVisible();
    await table.getByRole('button', { name: 'Show answer' }).first().click();
    await expect(table.getByText('Answer given:')).toBeVisible();
    await expect(table.getByText('Opening an answer is recorded in the access log.')).toBeVisible();
  });

  test('a viewer can open a dimension but is never offered the source answers', async ({ page }) => {
    await signIn(page, /^Viewer\b(?!,)/);
    await page.getByRole('link', { name: 'Detailed analysis: Team Health' }).click();
    await expect(page.getByRole('heading', { name: 'Team Health', level: 1 })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Show answer' })).toHaveCount(0);
    await expect(page.getByText('Question detail is not available')).toBeVisible();
  });

  test('a pattern explains itself: conditions met, evidence, penalty and actions', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await page.getByRole('link', { name: 'Pattern details: Team burnout risk' }).click();
    await expect(page.getByRole('heading', { name: 'Why this pattern was detected' })).toBeVisible();
    const tree = page.getByRole('list', { name: 'Matched conditions' });
    await expect(tree).toContainText('Workload pressure is at least 75');
    await expect(tree).toContainText('Team sustainability is at most 35');
    await expect(page.getByRole('link', { name: 'Burnout risk deduction' })).toBeVisible();
    await expect(page.getByText('Review team capacity')).toBeVisible();
  });

  test('a penalty shows requested against effective and how it was decided', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await page.getByRole('link', { name: 'Penalty details: Burnout risk deduction' }).click();
    await expect(page.getByRole('heading', { name: 'This deduction changed the score' })).toBeVisible();
    await expect(page.getByRole('img', { name: 'Requested deduction 12.00' })).toBeVisible();
    await expect(page.getByRole('img', { name: 'Effective deduction 12.00' })).toBeVisible();
    await expect(page.getByText('Aggregation policy').locator('xpath=following-sibling::dd')).toContainText('Max per category');
  });

  test('the previous assessment can be compared, and the form compares two chosen ones', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await page.getByRole('link', { name: 'Compare with the previous assessment' }).click();
    await expect(page.getByRole('heading', { name: 'Compare assessments' })).toBeVisible();
    await expect(page.getByText(/these are comparable/)).toBeVisible();
    await expect(page.getByRole('table', { name: 'Dimension scores in both assessments' })).toBeVisible();

    await page.getByRole('link', { name: 'Compare', exact: true }).click();
    await expect(page.getByText('Choose two assessments')).toBeVisible();
    await page.getByRole('button', { name: 'Compare' }).click();
    await expect(page.getByRole('alert')).toHaveCount(2);
  });

  test('another tenant cannot open a run it does not own', async ({ page, request }) => {
    await signIn(page, /^Administrator Full access/);
    const href = await page.getByRole('link', { name: 'How was this calculated?' }).getAttribute('href');
    await page.getByRole('button', { name: 'Sign out' }).click();
    await page.goto('/login');
    await page.getByRole('button', { name: /^Administrator of another tenant/ }).click();
    await page.waitForURL(/\/dashboard$/);
    await page.goto(href as string);
    await expect(page.getByRole('alert')).toContainText('Not found');
    expect(request).toBeTruthy();
  });

  test('a viewer reads the explanation with question-level detail hidden', async ({ page }) => {
    await signIn(page, /^Viewer\b(?!,)/);
    await page.getByRole('link', { name: 'How was this calculated?' }).click();
    await expect(page.getByRole('note')).toContainText('hidden for your role');
    await expect(page.getByRole('table', { name: 'Normalized answers' })).toHaveCount(0);
    await expect(page.getByRole('img', { name: /Final score/ })).toBeVisible();
  });
});
