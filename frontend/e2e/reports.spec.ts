import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

async function expectNoViolations(page: Page, what: string) {
  // a button caught mid hover-transition can read as low contrast, so the pointer moves away first
  await page.mouse.move(0, 0);
  await page.waitForTimeout(300);
  const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  expect(summary, `${what} has accessibility violations`).toEqual([]);
}

async function signIn(page: Page, persona: RegExp) {
  await page.goto('/login');
  await page.getByRole('button', { name: persona }).click();
  await expect(page.getByTestId('lis-value')).toBeVisible();
}

/** Opens a report of the newest assessment (or of two, for the comparison) through the form. */
async function showReport(page: Page, label: string, title: string) {
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Reports' }).click();
  await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible();
  await expect(page.getByText('Choose a report')).toBeVisible();
  await page.getByLabel('Report', { exact: true }).selectOption({ label });
  if (label === 'Historical Comparison') {
    await page.getByLabel('Earlier assessment').selectOption({ index: 2 });
    await page.getByLabel('Later assessment').selectOption({ index: 1 });
  } else {
    await page.getByLabel('Assessment', { exact: true }).selectOption({ index: 1 });
  }
  await page.getByRole('button', { name: 'Show report' }).click();
  await expect(page.getByRole('heading', { name: title, level: 2 })).toBeVisible();
}

test.describe('reports', () => {
  test('an administrator reads a report, and the page, the PDF and the CSV say the same', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await showReport(page, 'Leadership Intelligence', 'Leadership Intelligence Report');

    await expect(page.getByText(/not an individual performance rating/).first()).toBeVisible();
    const summary = page.getByRole('table', { name: 'Summary', exact: true });
    await expect(summary.getByRole('row', { name: /Leadership Intelligence Score/ })).toBeVisible();
    await expect(page.getByRole('table', { name: 'Dimension scores', exact: true })).toBeVisible();
    await expect(page.getByRole('table', { name: 'Methodology and scoring version' })).toContainText('Unvalidated');
    const score = (await summary.getByRole('row', { name: /Leadership Intelligence Score/ }).innerText()).replace(/\s+/g, ' ');

    const pdfEvent = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download PDF' }).click();
    const pdf = await pdfEvent;
    expect(pdf.suggestedFilename()).toMatch(/^leadership-intelligence-.*\.pdf$/);
    expect((await readFile(await pdf.path())).subarray(0, 5).toString('latin1')).toBe('%PDF-');
    await expect(page.getByRole('status')).toContainText('Saved leadership-intelligence-');

    const csvEvent = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV' }).click();
    const csv = await csvEvent;
    expect(csv.suggestedFilename()).toMatch(/\.csv$/);
    const text = (await readFile(await csv.path())).toString('utf8');
    expect(text.startsWith('﻿Leadership Intelligence Report')).toBe(true);
    expect(text).toContain(score.split(' ').slice(-1)[0]);
  });

  test('every report type can be read, and the comparison needs two assessments', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await showReport(page, 'Dimension Analysis', 'Dimension Analysis Report');
    await expect(page.getByRole('table', { name: 'What each question contributed' })).toBeVisible();

    await page.getByLabel('Report', { exact: true }).selectOption({ label: 'Pattern and Penalty' });
    await page.getByRole('button', { name: 'Show report' }).click();
    await expect(page.getByRole('heading', { name: 'Pattern and Penalty Report', level: 2 })).toBeVisible();
    await expect(page.getByRole('table', { name: 'Penalties and their outcome' })).toBeVisible();

    await page.getByLabel('Report', { exact: true }).selectOption({ label: 'Historical Comparison' });
    await page.getByRole('button', { name: 'Show report' }).click();
    await expect(page.getByText('Choose the earlier assessment')).toBeVisible();
    await page.getByLabel('Earlier assessment').selectOption({ index: 2 });
    await page.getByLabel('Later assessment').selectOption({ index: 1 });
    await page.getByRole('button', { name: 'Show report' }).click();
    await expect(page.getByRole('heading', { name: 'Historical Comparison Report', level: 2 })).toBeVisible();
    await expect(page.getByRole('table', { name: 'Can the runs be compared' })).toBeVisible();
    await expect(page).toHaveURL(/type=HISTORICAL_COMPARISON.*base=/);
  });

  test('a viewer reads the report without the values behind it', async ({ page }) => {
    await signIn(page, /^Viewer\b(?!,)/);
    await showReport(page, 'Dimension Analysis', 'Dimension Analysis Report');
    await expect(page.getByRole('note')).toContainText('Some values are withheld for your role.');
    await expect(page.getByText('Per-question values are withheld for your role.')).toBeVisible();
    await expect(page.getByRole('table', { name: 'What each question contributed' })).toHaveCount(0);
  });

  test('another tenant has no assessments to report on', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /^Administrator of another tenant/ }).click();
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Reports' }).click();
    await expect(page.getByLabel('Assessment', { exact: true }).locator('option')).toHaveCount(1);
  });

  for (const theme of ['dark', 'light']) {
    test(`the reports page is accessible and fits a phone (${theme})`, async ({ page }) => {
      if (theme === 'light') await page.addInitScript(() => window.localStorage.setItem('lis.theme', 'light'));
      await signIn(page, /^Administrator Full access/);
      await page.goto('/reports');
      await expect(page.getByText('Choose a report')).toBeVisible();
      await expectNoViolations(page, `reports, empty (${theme})`);
      await showReport(page, 'Leadership Intelligence', 'Leadership Intelligence Report');
      await expectNoViolations(page, `leadership report (${theme})`);

      await page.setViewportSize({ width: 390, height: 800 });
      await expect(page.getByRole('heading', { name: 'Leadership Intelligence Report', level: 2 })).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }
});
