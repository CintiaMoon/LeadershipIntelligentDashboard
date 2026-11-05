import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// WCAG 2.2 AA automated checks on the pages that need no sign-in, in both themes, and a check that none of them raises a console error
// or a content-security-policy violation. Automated checks find roughly a third of accessibility problems; the manual checklist in
// docs/release/ covers the rest. These pages make no account request until a form is sent, so they leave the throttles alone.
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

const PAGES: Array<[string, string, string]> = [
  ['sign in', '/login', 'Sign in'],
  ['register', '/register', 'Create an account'],
  ['reset request', '/forgot-password', 'Reset your password'],
  ['reset with a link', '/reset-password?token=example', 'Choose a new password'],
  ['confirm with a link', '/verify-email?token=example', 'Confirm your email address'],
  ['confirmation again', '/verify-email', 'Send the confirmation link again'],
  ['a link without its token', '/invitations/accept', 'This link is incomplete'],
];

async function expectNoViolations(page: Page, what: string) {
  const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  expect(summary, `${what} has accessibility violations`).toEqual([]);
}

for (const theme of ['dark', 'light'] as const) {
  test.describe(`the account pages, ${theme} theme`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((value) => window.localStorage.setItem('lis.theme', value), theme);
    });

    for (const [name, path, heading] of PAGES) {
      test(`${name} has no automated WCAG 2.2 AA violations and fits a phone`, async ({ page }) => {
        const problems: string[] = [];
        page.on('console', (message) => {
          // the browser notes the 401 that answers "nobody is signed in" to a visitor (the page handles it); anything else is a problem
          if (message.type() === 'error' && !/^Failed to load resource: the server responded with a status of 401/.test(message.text())) problems.push(message.text());
        });
        page.on('pageerror', (error) => problems.push(error.message));

        await page.setViewportSize({ width: 375, height: 700 });
        await page.goto(path);
        await expect(page.getByRole('heading', { name: heading, level: 1 })).toBeVisible();
        // dark is the default and carries no attribute; light sets it
        if (theme === 'light') await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
        else await expect(page.locator('html')).not.toHaveAttribute('data-theme', 'light');
        await expectNoViolations(page, `${name} (${theme})`);
        // nothing scrolls sideways on a phone
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow, 'horizontal overflow in pixels').toBeLessThanOrEqual(0);
        expect(problems, 'console errors or policy violations').toEqual([]);
      });
    }
  });
}

test('the password can be shown and hidden from the keyboard', async ({ page }) => {
  await page.goto('/login');
  const field = page.getByLabel('Password', { exact: true });
  await expect(field).toHaveAttribute('type', 'password');
  await page.getByRole('button', { name: 'Show' }).focus();
  await page.keyboard.press('Enter');
  await expect(field).toHaveAttribute('type', 'text');
  await expect(page.getByRole('button', { name: 'Hide' })).toHaveAttribute('aria-pressed', 'true');
});
