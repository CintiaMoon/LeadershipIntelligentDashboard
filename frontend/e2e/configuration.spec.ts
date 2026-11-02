import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Browser, type Page } from '@playwright/test';

// Managing the scoring configuration (decisions D52 to D58). Everything happens in the scratch organization org-assess as an
// organization override, so the seeded series of org-1 and org-2 never change. Two people are needed: the second-person rule
// forbids the author from approving.

const LAYER = 'Organization override org-assess';
const nav = (page: Page) => page.getByRole('navigation', { name: 'Main' });

async function signIn(page: Page, persona: RegExp) {
  await page.goto('/login');
  await page.getByRole('button', { name: persona }).click();
  await page.waitForURL(/\/dashboard$/);
}

async function asOwner(browser: Browser) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await signIn(page, /^Assessments owner/);
  return { context, page };
}

async function expectNoViolations(page: Page, what: string) {
  // a button still easing into its hover colour right after a click is not what a reader sees
  await page.mouse.move(0, 0);
  await page.waitForTimeout(300);
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  expect(summary, `${what} has accessibility violations`).toEqual([]);
}

async function openLayer(page: Page) {
  await nav(page).getByRole('link', { name: 'Configuration' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Configuration' })).toBeVisible();
  await page.getByLabel(LAYER).getByRole('link', { name: 'Versions and fixtures' }).click();
  await expect(page.getByRole('heading', { level: 1, name: LAYER })).toBeVisible();
}

test.describe.configure({ mode: 'serial' });

test.describe('configuration management', () => {
  const fixtureName = `E2E impossible ${Date.now()}`;
  let draftUrl = '';

  test('an administrator drafts an override in the editor, checks it, previews it and submits it', async ({ page }) => {
    test.setTimeout(120_000);
    await signIn(page, /^Assessments administrator/);
    await openLayer(page);

    // a layer with no version has nowhere to keep fixtures, so the draft comes first
    await page.getByRole('button', { name: 'New draft', exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: /^Version \d+$/ })).toBeVisible();
    draftUrl = page.url();
    await expect(page.getByText('Draft', { exact: true }).first()).toBeVisible();

    // fixtures left behind by an earlier run would block approval for the wrong reason
    await page.getByRole('link', { name: LAYER }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByLabel('Fixtures')).toBeVisible();
    await expect(page.getByText('Loading the fixtures')).toHaveCount(0);
    for (;;) {
      const stale = page.getByRole('button', { name: /^Delete fixture E2E impossible/ });
      if ((await stale.count()) === 0) break;
      const label = (await stale.first().getAttribute('aria-label')) as string;
      await stale.first().click();
      await page.getByRole('button', { name: label.replace('Delete fixture ', 'Delete ') }).click();
      await expect(page.getByRole('button', { name: label })).toHaveCount(0);
    }

    // a failing fixture: approval must be blocked while it fails
    await page.getByRole('button', { name: 'Add a fixture' }).click();
    await page.getByLabel('Name').fill(fixtureName);
    await page.getByLabel('Answers (JSON)').fill('[{ "questionId": "Q01", "answer": 5 }]');
    await page.getByLabel('What must hold (JSON)').fill('{ "overall": { "min": 0, "max": 1 } }');
    await page.getByRole('button', { name: 'Save fixture' }).click();
    await expect(page.getByText(fixtureName)).toBeVisible();

    await page.goto(draftUrl);
    await expect(page.getByRole('heading', { level: 1, name: /^Version \d+$/ })).toBeVisible();

    // text that is not JSON cannot be saved, and the editor says so as you type
    await page.getByRole('button', { name: 'JSON', exact: true }).click();
    const editor = page.getByLabel('Document (JSON)');
    const original = await editor.inputValue();
    await editor.fill('{ "questions": ');
    await expect(page.getByRole('button', { name: 'Save draft' })).toBeDisabled();
    await expect(page.locator('#config-document-status')).not.toContainText('Valid JSON');
    await editor.fill(original);
    await expect(page.locator('#config-document-status')).toContainText('Valid JSON');

    await page.getByRole('button', { name: 'Check' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'No problems found' })).toBeVisible();

    await page.getByRole('button', { name: 'Preview' }).click();
    await expect(page.getByRole('region', { name: 'Preview' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Preview' }).getByText(fixtureName)).toBeVisible();
    await expect(page.getByRole('region', { name: 'Preview' }).getByRole('row', { name: new RegExp(fixtureName) })).toContainText('Fails');

    await page.getByRole('button', { name: 'Submit for approval' }).click();
    await expect(page.getByText('Awaiting approval').first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Approve or send back' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save draft' })).toHaveCount(0);
  });

  test('the author cannot approve; the owner is blocked by the failing fixture, then approves once it is gone', async ({ page, browser }) => {
    test.setTimeout(120_000);
    await signIn(page, /^Assessments administrator/);
    await page.goto(draftUrl);
    await page.getByLabel('Note').fill('Looks right');
    await page.getByRole('button', { name: 'Approve and activate' }).click();
    await expect(page.getByRole('alert').filter({ hasText: /Not allowed: .*did not create, edit or submit it/ }).first()).toBeVisible();

    const owner = await asOwner(browser);
    try {
      await owner.page.goto(draftUrl);
      await expect(owner.page.getByRole('heading', { name: 'Approve or send back' })).toBeVisible();
      await owner.page.getByLabel('Note').fill('Checked the override against the fixtures');
      await owner.page.getByRole('button', { name: 'Approve and activate' }).click();
      await expect(owner.page.getByRole('alert').filter({ hasText: fixtureName })).toBeVisible();
      await expect(owner.page.getByText('Awaiting approval').first()).toBeVisible();

      // remove the fixture that cannot hold, from the layer page, then approve
      await owner.page.getByRole('link', { name: LAYER }).click();
      await owner.page.getByRole('button', { name: `Delete fixture ${fixtureName}` }).click();
      await owner.page.getByRole('button', { name: `Delete ${fixtureName}` }).click();
      await expect(owner.page.getByText(fixtureName)).toHaveCount(0);

      await owner.page.goto(draftUrl);
      await owner.page.getByLabel('Note').fill('Checked the override against the fixtures');
      await owner.page.getByLabel('Calibration reference (optional)').fill('E2E: no real data');
      await owner.page.getByRole('button', { name: 'Approve and activate' }).click();
      await expect(owner.page.getByText('Active', { exact: true }).first()).toBeVisible();
      await expect(owner.page.getByRole('region', { name: 'Review record' })).toContainText('Assessments owner'.length ? 'assess-owner-user' : '');
      await expect(owner.page.getByRole('region', { name: 'Review record' })).toContainText('E2E: no real data');
      await expect(owner.page.getByRole('button', { name: 'Save draft' })).toHaveCount(0);
      await owner.page.getByRole('button', { name: 'JSON', exact: true }).click();
      await expect(owner.page.getByLabel('Document (JSON)')).toHaveAttribute('readonly', '');
    } finally {
      await owner.context.close();
    }
  });

  test('an analyst can read the configuration and change nothing', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /^Analyst Reads/ }).click();
    await page.waitForURL(/\/dashboard$/);
    await nav(page).getByRole('link', { name: 'Configuration' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Configuration' })).toBeVisible();
    await expect(page.getByRole('button', { name: /New draft/ })).toHaveCount(0);
    await page.goto(draftUrl.replace(/^https?:\/\/[^/]+/, ''));
    // the analyst is not in org-assess, so the version is not theirs to see
    await expect(page.getByRole('heading', { level: 1, name: /^Version \d+$/ })).toHaveCount(0);
  });

  for (const theme of ['dark', 'light'] as const) {
    test(`the configuration screens have no accessibility violations and fit a phone (${theme})`, async ({ page }) => {
      test.setTimeout(120_000);
      if (theme === 'light') await page.addInitScript(() => window.localStorage.setItem('lis.theme', 'light'));
      await signIn(page, /^Assessments administrator/);
      const visit = async (what: string, path: string, heading: string | RegExp) => {
        await page.goto(path);
        await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
        await page.waitForLoadState('networkidle');
        await expectNoViolations(page, what);
      };
      await visit('the layers', '/configuration', 'Configuration');
      await visit('a layer', '/configuration/layers/organization/org-assess', LAYER);
      await visit('a version', draftUrl.replace(/^https?:\/\/[^/]+/, ''), /^Version \d+$/);

      await page.setViewportSize({ width: 390, height: 844 });
      for (const path of ['/configuration', '/configuration/layers/organization/org-assess', draftUrl.replace(/^https?:\/\/[^/]+/, '')]) {
        await page.goto(path);
        await page.waitForLoadState('networkidle');
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow, `${path} scrolls horizontally on a phone`).toBeLessThanOrEqual(1);
      }
    });
  }
});

test.describe('platform default', () => {
  test('the platform administrator sees the platform layer and the tenant roles cannot reach it', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /^Platform administrator/ }).click();
    await expect(page.getByRole('heading', { name: 'Tenants', level: 1 })).toBeVisible();
    await nav(page).getByRole('link', { name: 'Configuration' }).click();
    const card = page.getByLabel('Platform default');
    await expect(card).toBeVisible();
    await expect(card.getByText('Unvalidated')).toBeVisible();
    await card.getByRole('link', { name: 'Versions and fixtures' }).click();
    await expect(page.getByRole('table', { name: /Versions of Platform default/ })).toBeVisible();
    await expect(page.getByLabel('Fixtures')).toBeVisible();
    await expectNoViolations(page, 'the platform layer');
  });
});

test.describe('forms', () => {
  for (const theme of ['dark', 'light'] as const) {
    test(`the platform administrator edits the numbers and wording with forms (${theme})`, async ({ page }) => {
      test.setTimeout(120_000);
      if (theme === 'light') await page.addInitScript(() => window.localStorage.setItem('lis.theme', 'light'));
      await page.goto('/login');
      await page.getByRole('button', { name: /^Platform administrator/ }).click();
      await expect(page.getByRole('heading', { name: 'Tenants', level: 1 })).toBeVisible();
      await page.goto('/configuration/layers/platform');
      await page.getByRole('button', { name: 'New draft', exact: true }).click();
      await expect(page.getByRole('heading', { level: 1, name: /^Version \d+$/ })).toBeVisible();

      // the overall score: weights that do not add up are called out at once, and the comparison follows the form
      await expect(page.getByRole('button', { name: 'Overall score', exact: true })).toHaveAttribute('aria-pressed', 'true');
      const weight = page.getByLabel('Team health', { exact: true });
      await weight.fill('0.4');
      await expect(page.getByRole('alert').filter({ hasText: /add up to 1.15/ })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Save draft' })).toBeEnabled();
      await expect(page.getByText(/Compared with active version/)).toBeVisible();
      await expectNoViolations(page, 'the overall form');

      await page.getByRole('button', { name: 'Coverage and group answers' }).click();
      await expectNoViolations(page, 'the coverage form');
      await page.getByRole('button', { name: 'Penalties', exact: true }).click();
      await expect(page.getByRole('group', { name: /^TH-P01/ })).toBeVisible();
      await expectNoViolations(page, 'the penalties form');
      await page.getByRole('button', { name: 'Advice' }).click();
      await expectNoViolations(page, 'the advice form');
      await page.getByRole('button', { name: 'Display texts' }).click();
      await page.getByText(/^Dimensions \(/).click();
      await expectNoViolations(page, 'the texts form');

      // the same document is in the JSON view
      await page.getByRole('button', { name: 'JSON', exact: true }).click();
      await expect(page.getByLabel('Document (JSON)')).toHaveValue(/"TEAM_HEALTH": 0\.4/);

      // a phone
      await page.getByRole('button', { name: 'Form', exact: true }).click();
      await page.getByRole('button', { name: 'Penalties', exact: true }).click();
      await page.setViewportSize({ width: 390, height: 844 });
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, 'the penalties form scrolls horizontally on a phone').toBeLessThanOrEqual(1);

      // nothing is approved: the draft stays a draft (the lifecycle has no way to discard one), so the platform default is unchanged
      await expect(page.getByText('Draft', { exact: true }).first()).toBeVisible();
    });
  }
});
