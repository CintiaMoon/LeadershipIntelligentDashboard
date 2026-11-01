import AxeBuilder from '@axe-core/playwright';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { asPersona, runsOf } from './api';

// WCAG 2.2 AA automated checks on every screen, plus the keyboard paths automation cannot see. Automated checks find
// roughly a third of accessibility problems, so the manual checklist in docs/release/ covers the rest.
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

async function expectNoViolations(page: Page, what: string) {
  const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  expect(summary, `${what} has accessibility violations`).toEqual([]);
}

/** The light theme is a stored per-browser choice, so a test sets it before the page loads. */
async function useLightTheme(page: Page) {
  await page.addInitScript(() => window.localStorage.setItem('lis.theme', 'light'));
}

async function signIn(page: Page, persona: RegExp) {
  await page.goto('/login');
  await page.getByRole('button', { name: persona }).click();
  await page.waitForURL(/\/dashboard$/);
  await expect(page.getByTestId('lis-value')).toBeVisible();
}

test.describe('accessibility', () => {
  test('the login page', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Leadership Intelligent Dashboard', level: 1 })).toBeVisible();
    await expectNoViolations(page, 'login');
  });

  test('the login page in the light theme', async ({ page }) => {
    await useLightTheme(page);
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Leadership Intelligent Dashboard', level: 1 })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await expectNoViolations(page, 'login (light)');
  });

  async function scanEverySignedInScreen(page: Page, request: APIRequestContext, theme: string) {
    // six axe scans and five full page loads in one test: about 6 s alone, but over 30 s when the other workers load the machine
    const admin = await asPersona(request, 'admin');
    const runs = await runsOf(admin, 'org-2');
    const run = runs[2].scoringRunId as string;
    await signIn(page, /^Administrator Full access/);
    await page.getByRole('banner').getByLabel('Organization').selectOption('org-2');
    await expect(page.getByTestId('lis-value')).toBeVisible();
    await page.getByText('View the data').first().click(); // chart data tables are part of the page
    await expectNoViolations(page, `dashboard (${theme})`);

    for (const [name, path, ready] of [
      ['explanation', `/runs/${run}/explanation`, 'How the Leadership Intelligence Score was calculated'],
      ['dimension', `/runs/${run}/dimensions/TEAM_HEALTH`, 'Team Health'],
      ['pattern', `/runs/${run}/patterns/TH-001`, 'Team burnout risk'],
      ['penalty', `/runs/${run}/penalties/TH-P02`, 'Key-person dependency deduction'],
      ['comparison', `/compare?base=${runs[1].scoringRunId}&run=${run}`, 'Compare assessments'],
    ] as const) {
      await page.goto(path);
      await expect(page.getByRole('heading', { name: ready, level: 1 })).toBeVisible();
      if (name === 'explanation') await page.evaluate(() => document.querySelectorAll('details').forEach((d) => (d.open = true)));
      if (name === 'comparison') await expect(page.getByRole('table', { name: 'Dimension scores in both assessments' })).toBeVisible();
      await expectNoViolations(page, `${name} (${theme})`);
    }
  }

  test('every signed-in screen has no automated WCAG 2.2 AA violations (dark theme)', async ({ page, request }) => {
    test.setTimeout(120_000);
    await scanEverySignedInScreen(page, request, 'dark');
  });

  test('every signed-in screen has no automated WCAG 2.2 AA violations (light theme)', async ({ page, request }) => {
    test.setTimeout(120_000);
    await useLightTheme(page);
    await scanEverySignedInScreen(page, request, 'light');
  });

  test('the theme switch works from the keyboard and survives a reload', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await expect(page.locator('html')).not.toHaveAttribute('data-theme', /.*/);
    const toggle = page.getByRole('button', { name: 'Switch to light theme' });
    await toggle.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await expect(page.getByRole('button', { name: 'Switch to dark theme' })).toBeVisible();
    // the colours really change: the page background is light after the switch
    const background = await page.evaluate(() => getComputedStyle(document.body.parentElement as HTMLElement).backgroundColor);
    expect(background).toBe('rgb(246, 247, 249)');
  });

  test('the not-found page and an error state', async ({ page }) => {
    await signIn(page, /^Analyst\b/);
    await page.goto('/nowhere');
    await expect(page.getByText('Page not found')).toBeVisible();
    await expectNoViolations(page, 'not found');
  });

  test('the dashboard can be used with the keyboard alone', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    await page.goto('/dashboard');
    await expect(page.getByTestId('lis-value')).toBeVisible();
    // the first Tab stop is the skip link, and activating it moves focus to the main content
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/#main$/);
    // the severity filter works from the keyboard and reports its state
    const critical = page.getByRole('button', { name: /Critical/ });
    await critical.focus();
    await page.keyboard.press('Enter');
    await expect(critical).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByText(/Showing Critical severity only/)).toBeVisible();
    // evidence opens and closes from the keyboard and announces its state
    await page.getByRole('button', { name: 'Clear filter' }).focus();
    await page.keyboard.press('Enter');
    const toggle = page.getByRole('button', { name: 'Show evidence' }).first();
    await toggle.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('button', { name: 'Hide evidence' }).first()).toHaveAttribute('aria-expanded', 'true');
  });

  test('colour is never the only signal: severity and state are written out', async ({ page }) => {
    await signIn(page, /^Administrator Full access/);
    for (const severity of ['Critical', 'Medium']) {
      await expect(page.getByRole('article', { name: /./ }).getByText(new RegExp(severity)).first()).toBeVisible();
    }
    await expect(page.getByRole('group', { name: 'Suppressed' }).getByText('Suppressed', { exact: true }).first()).toBeVisible();
  });

  test('the layout holds on a phone: no horizontal scrolling on any main screen', async ({ page, request }) => {
    const admin = await asPersona(request, 'admin');
    const run = (await runsOf(admin, 'org-2'))[2].scoringRunId as string;
    await signIn(page, /^Administrator Full access/);
    await page.setViewportSize({ width: 390, height: 844 });
    for (const path of ['/dashboard', `/runs/${run}/explanation`, `/runs/${run}/dimensions/TEAM_HEALTH`, `/runs/${run}/patterns/TH-001`, `/runs/${run}/penalties/TH-P01`]) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${path} overflows horizontally`).toBeLessThanOrEqual(1);
    }
  });
});
