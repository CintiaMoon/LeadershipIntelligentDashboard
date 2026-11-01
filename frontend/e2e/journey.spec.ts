import { expect, test } from '@playwright/test';
import { asPersona, runsOf } from './api';

// The ten steps of the brief (section 13), in order, on the deterministic seed data of organization org-2:
// 81.25, 87.50, 67.00 (comparable) and 67.00 again on template version 2 (not comparable with the run before).
test('the executive journey from sign-in to comparing historical runs', async ({ page, request }) => {
  const admin = await asPersona(request, 'admin');
  const runs = await runsOf(admin, 'org-2');
  expect(runs.map((r) => r.leadershipIntelligenceScore)).toEqual([81.25, 87.5, 67, 67]);

  await test.step('1. the user authenticates', async () => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
    await page.getByRole('button', { name: /^Administrator Full access/ }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByLabel('Your roles')).toContainText('Admin');
  });

  await test.step('2. the user selects an organization', async () => {
    await page.getByRole('banner').getByLabel('Organization').selectOption('org-2');
    await expect(page.getByText('Organization org-2', { exact: true })).toBeVisible();
    await expect(page.getByTestId('lis-value')).toBeVisible();
  });

  await test.step('3. the user opens the scoring dashboard', async () => {
    for (const name of ['Team Health', 'Delivery Stability', 'Technical Risk', 'Leadership Load']) {
      await expect(page.getByRole('heading', { name, level: 3, exact: true })).toBeVisible();
    }
    await expect(page.getByRole('heading', { name: 'Risk overview' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Historical trends' })).toBeVisible();
  });

  await test.step('4. the user inspects the LIS', async () => {
    await expect(page.getByTestId('lis-value')).toHaveText('67.00');
    await expect(page.getByText('Healthy').first()).toBeVisible();
    await expect(page.getByText(/Band 60.80/)).toBeVisible();
    await expect(page.getByText(/oldest to newest: 81\.25, 87\.50, 67\.00, 67\.00/).first()).toBeVisible();
    // the latest run is on another template version, so no change is claimed
    const note = page.getByRole('note').first();
    await expect(note).toContainText('Not comparable with the previous assessment (67.00)');
    await expect(note).toContainText('the assessment template version changed');
    await expect(page.getByText(/not an individual performance rating/).first()).toBeVisible();
  });

  await test.step('5. the user opens a dimension', async () => {
    await page.getByRole('link', { name: 'Detailed analysis: Team Health' }).click();
    await expect(page.getByRole('heading', { name: 'Team Health', level: 1 })).toBeVisible();
    await expect(page.getByTestId('adjusted-score')).toHaveText('13.00');
    await expect(page.getByText('Raw score').locator('xpath=following-sibling::p[1]')).toHaveText('25.00');
    await expect(page.getByText('Penalty applied').locator('xpath=following-sibling::p[1]')).toHaveText('12.00');
    await expect(page.getByRole('table', { name: 'Contributions to Team Health' })).toBeVisible();
  });

  await test.step('6. the user inspects a pattern', async () => {
    await page.getByRole('link', { name: 'Team burnout risk' }).first().click();
    await expect(page.getByRole('heading', { name: 'Team burnout risk', level: 1 })).toBeVisible();
    const tree = page.getByRole('list', { name: 'Matched conditions' });
    await expect(tree).toContainText('All of these must be met');
    await expect(tree).toContainText('Workload pressure is at least 75 (it was 80.00)');
    await expect(tree).toContainText('Team sustainability is at most 35 (it was 25.00)');
  });

  await test.step('7. the user reviews the evidence', async () => {
    const evidence = page.getByRole('heading', { name: 'Evidence' }).locator('xpath=ancestor::section[1]');
    await expect(evidence.getByText('Workload pressure', { exact: true })).toBeVisible();
    await expect(evidence.getByText('80.00')).toBeVisible();
    await expect(evidence.getByText('25.00')).toBeVisible();
  });

  await test.step('8. the user inspects applied and suppressed penalties', async () => {
    await page.getByRole('banner').getByRole('link', { name: 'Dashboard' }).click();
    const applied = page.getByRole('group', { name: 'Applied' });
    await expect(applied.getByRole('article', { name: 'Burnout risk deduction' })).toContainText('12.00');
    const suppressed = page.getByRole('group', { name: 'Suppressed' });
    const card = suppressed.getByRole('article', { name: 'Key-person dependency deduction' });
    await expect(card).toContainText('Suppressed');
    await expect(card).toContainText('a larger penalty in the same category applies instead');
    await page.getByRole('link', { name: 'Penalty details: Key-person dependency deduction' }).click();
    await expect(page.getByRole('heading', { name: 'This is a candidate, not an applied deduction' })).toBeVisible();
    await expect(page.getByRole('img', { name: 'Requested deduction 5.00' })).toBeVisible();
    await expect(page.getByRole('img', { name: 'Effective deduction 0.00' })).toBeVisible();
  });

  await test.step('9. the user opens the LIS explanation', async () => {
    await page.getByRole('banner').getByRole('link', { name: 'Dashboard' }).click();
    await page.getByRole('link', { name: 'How was this calculated?' }).click();
    await expect(page.getByRole('heading', { name: 'How the Leadership Intelligence Score was calculated' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Calculation steps' }).getByRole('link')).toHaveCount(8);
    const final = page.getByRole('heading', { name: 'Final LIS' }).locator('xpath=ancestor::details');
    await expect(final.getByRole('img', { name: 'Final score 67.00' })).toBeVisible();
    await page.getByRole('heading', { name: 'Penalties', level: 2 }).click();
    const table = page.getByRole('table', { name: 'Penalties' });
    await expect(table.getByRole('row', { name: /Burnout risk deduction/ })).toContainText('Applied');
    await expect(table.getByRole('row', { name: /Key-person dependency deduction/ })).toContainText('Suppressed');
  });

  await test.step('10. the user compares historical scoring runs', async () => {
    await page.getByRole('banner').getByRole('link', { name: 'Compare' }).click();
    await page.getByLabel(/Earlier assessment/).selectOption(runs[1].scoringRunId); // 87.50
    await page.getByLabel(/Later assessment/).selectOption(runs[2].scoringRunId); // 67.00, same basis
    await page.getByRole('button', { name: 'Compare' }).click();
    await expect(page.getByText(/these are comparable/)).toContainText('-20.50');
    await expect(page.getByRole('table', { name: 'Dimension scores in both assessments' }).getByRole('row', { name: /Team Health/ })).toContainText('-');

    // against the run on another template version, nothing is claimed and the difference is named
    await page.getByLabel(/Earlier assessment/).selectOption(runs[2].scoringRunId);
    await page.getByLabel(/Later assessment/).selectOption(runs[3].scoringRunId);
    await page.getByRole('button', { name: 'Compare' }).click();
    const note = page.getByRole('note');
    await expect(note).toContainText('not comparable');
    await expect(note).toContainText('the assessment template version changed');
    const diff = page.getByRole('table', { name: 'Scoring basis of the two assessments' });
    await expect(diff.getByRole('row', { name: /Template version/ })).toContainText('Differs');
    await expect(diff.getByRole('row', { name: /Scoring configuration/ })).not.toContainText('Differs');
  });
});
