import { expect, test as base, type Page } from '@playwright/test';
import { accountFor, emailOf, PASSWORD } from './accounts';

export { expect, base as test };
export type { Page };

/** The company in the address of each page that signed in (the screens of a company live under /c/<company>). */
const companies = new WeakMap<Page, string>();
const wrapped = new WeakSet<Page>();
const OUTSIDE = /^\/(login|companies|account|register|verify-email|forgot-password|reset-password|invitations)(\/|\?|$)/;

/** From now on an address such as /dashboard is opened inside the company the page signed in to (the specs name the screens, not the prefix). */
function insideTheCompany(page: Page): void {
  if (wrapped.has(page)) return;
  wrapped.add(page);
  const goto = page.goto.bind(page);
  page.goto = ((url: string, options?: Parameters<Page['goto']>[1]) => {
    const company = companies.get(page);
    const inside = company && url.startsWith('/') && !url.startsWith('/c/') && !OUTSIDE.test(url);
    return goto(inside ? `/c/${company}${url}` : url, options);
  }) as Page['goto'];
}

/**
 * Signs in with the form, as a person would, as the demo account that replaces a persona of the old picker. The page is on the
 * sign-in page or goes there. A person with one company ends in it; a platform administrator ends on the platform.
 */
export async function pickPersona(page: Page, picker: RegExp | string): Promise<void> {
  insideTheCompany(page);
  const account = typeof picker === 'string' ? picker : accountFor(picker);
  if (!page.url().endsWith('/login')) await page.goto('/login');
  await page.getByLabel('Email address').fill(emailOf(account));
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.waitForURL(/\/(c\/[^/]+\/|platform\/)/);
  const company = /^\/c\/([^/]+)\//.exec(new URL(page.url()).pathname)?.[1];
  if (company) companies.set(page, company);
  else companies.delete(page);
}
