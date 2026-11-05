import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { latestMail, linkIn } from './mail';

// The whole journey of the application's own accounts against the real stack (decisions D65 to D74): a platform administrator
// creates a company and invites its owner, the owner joins, invites an analyst, the analyst joins and is limited, a suspended member
// loses the company at once, an API token works until it is revoked, and the security audit shows it. Emails are read from Mailpit.
//
// Each person has one browser of their own for the whole run and signs in once, as in real use. The sign-in and the account
// requests are throttled (decision D71): a run asks for one password reset and makes about five sign-ins, so it can be repeated a
// few times an hour; if the throttle answers, wait, or clear the counters of the local stack with
// `docker compose exec redis redis-cli FLUSHALL`.

const BASE = process.env.E2E_ACCOUNTS_URL ?? 'http://localhost:3000';
const PASSWORD = 'correct horse battery';
const PLATFORM_PASSWORD = 'a platform administrator passphrase';
const ADMIN = 'platform-admin-3@demo.test'; // a demo platform administrator of its own (dev profile): this journey resets its password

const stamp = Date.now().toString(36);
const company = `e2e-${stamp}`;
const companyName = `E2E Company ${stamp}`;
const owner = `owner-${stamp}@e2e.test`;
const analyst = `analyst-${stamp}@e2e.test`;

async function fillSignIn(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
}

/** Signs in a person who has no company or is the platform administrator: they end on the company list or the platform. */
async function signIn(page: Page, email: string, password: string) {
  await fillSignIn(page, email, password);
  await page.waitForURL(/\/(companies|platform\/tenants)$/);
}

/** Signs in a person who belongs to the company: they end on its dashboard, in the address of the company. */
async function signInToCompany(page: Page, email: string, password: string) {
  await fillSignIn(page, email, password);
  await page.waitForURL(new RegExp(`/c/${company}/dashboard$`));
}

test.describe.serial('accounts, companies, members, API tokens and the audit', () => {
  let contexts: BrowserContext[] = [];
  let admin: Page;
  let ownerPage: Page;
  let analystPage: Page;

  test.beforeAll(async ({ browser }) => {
    const person = async () => {
      const context = await browser.newContext({ baseURL: BASE });
      contexts.push(context);
      return context.newPage();
    };
    admin = await person();
    ownerPage = await person();
    analystPage = await person();
  });

  test.afterAll(async () => {
    await Promise.all(contexts.map((c) => c.close()));
    contexts = [];
  });

  test('a platform administrator sets a password from an emailed link and creates a company with an owner', async ({ request }) => {
    const since = new Date(Date.now() - 2000);
    await admin.goto('/forgot-password');
    await admin.getByLabel('Email address').fill(ADMIN);
    await admin.getByRole('button', { name: 'Send the link' }).click();
    await expect(admin.getByText(/If an account exists for that address/)).toBeVisible();

    await admin.goto(linkIn(await latestMail(request, ADMIN, /Reset your password/, since)));
    await admin.getByLabel('New password', { exact: true }).fill(PLATFORM_PASSWORD);
    await admin.getByLabel('Repeat the new password').fill(PLATFORM_PASSWORD);
    await admin.getByRole('button', { name: 'Change password' }).click();
    await expect(admin.getByText('Password changed')).toBeVisible();

    await signIn(admin, ADMIN, PLATFORM_PASSWORD);
    await expect(admin).toHaveURL(/\/platform\/tenants$/);
    await admin.goto('/platform/tenants/new');
    await admin.getByLabel('Tenant id').fill(company);
    await admin.getByLabel('Name', { exact: true }).fill(companyName);
    await admin.getByLabel('Owner email address').fill(owner);
    await admin.getByRole('button', { name: 'Create tenant' }).click();
    await expect(admin).toHaveURL(/\/platform\/tenants$/);
    await expect(admin.getByText(company).first()).toBeVisible();

    // the platform administrator has no company: a company address is refused, and the platform audit shows the invitation
    await admin.goto(`/c/${company}/dashboard`);
    await expect(admin.getByText('You cannot open this company')).toBeVisible();
    await admin.goto('/platform/security-audit');
    await admin.getByLabel('Company id').fill(company);
    await admin.getByRole('button', { name: 'Show' }).click();
    await expect(admin.getByText('Membership invited').first()).toBeVisible();
  });

  test('the owner registers, confirms the address with the password, and has no company until the invitation is accepted', async ({ request }) => {
    const since = new Date(Date.now() - 2000);
    await ownerPage.goto('/register');
    await ownerPage.getByLabel('Your name').fill('Olivia Owner');
    await ownerPage.getByLabel('Email address').fill(owner);
    await ownerPage.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await ownerPage.getByLabel('Repeat the password').fill(PASSWORD);
    await ownerPage.getByRole('button', { name: 'Create account' }).click();
    await expect(ownerPage.getByText('Check your email')).toBeVisible();

    await ownerPage.goto(linkIn(await latestMail(request, owner, /Confirm your email address/, since)));
    await ownerPage.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await ownerPage.getByRole('button', { name: 'Confirm' }).click();
    await expect(ownerPage.getByText('Email address confirmed')).toBeVisible();

    await signIn(ownerPage, owner, PASSWORD);
    await expect(ownerPage).toHaveURL(/\/companies$/);
    await expect(ownerPage.getByText('You have no company yet')).toBeVisible();
  });

  test('the owner accepts the invitation and lands in the company, named in the address', async ({ request }) => {
    await ownerPage.goto(linkIn(await latestMail(request, owner, /You are invited to/, new Date(Date.now() - 600_000))));
    await expect(ownerPage.getByRole('heading', { name: `Join ${companyName}` })).toBeVisible();
    await ownerPage.getByRole('button', { name: 'Accept the invitation' }).click();
    await expect(ownerPage.getByText('You joined the company.')).toBeVisible();
    await ownerPage.getByRole('link', { name: 'Open my companies' }).click();

    await expect(ownerPage).toHaveURL(new RegExp(`/c/${company}/dashboard$`));
    await expect(ownerPage.getByTestId('company-name')).toHaveText(companyName);
    await expect(ownerPage.getByLabel('Your roles')).toContainText('Owner');
    for (const entry of ['Members', 'API tokens', 'Security audit']) {
      await expect(ownerPage.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: entry })).toBeVisible();
    }
  });

  test('the owner invites an analyst, who registers with the link and sees a narrower company', async ({ request }) => {
    await ownerPage.goto(`/c/${company}/members`);
    const since = new Date(Date.now() - 2000);
    await ownerPage.getByLabel('Email address').fill(analyst);
    await ownerPage.getByLabel('Viewer', { exact: true }).uncheck();
    await ownerPage.getByLabel('Analyst', { exact: true }).first().check();
    await ownerPage.getByRole('button', { name: 'Send the invitation' }).click();
    await expect(ownerPage.getByRole('status')).toContainText(`Invitation sent to ${analyst}`);
    await expect(ownerPage.getByRole('listitem').filter({ hasText: analyst })).toBeVisible();

    await analystPage.goto(linkIn(await latestMail(request, analyst, /You are invited to/, since)));
    await expect(analystPage.getByRole('heading', { name: `Join ${companyName}` })).toBeVisible();
    await analystPage.getByLabel('Your name').fill('Anna Analyst');
    await analystPage.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await analystPage.getByLabel('Repeat the password').fill(PASSWORD);
    await analystPage.getByRole('button', { name: 'Create account and join' }).click();
    await expect(analystPage.getByText(/Your account is ready and you are a member/)).toBeVisible();

    await signInToCompany(analystPage, analyst, PASSWORD);
    await expect(analystPage.getByLabel('Your roles')).toContainText('Analyst');
    await expect(analystPage.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Members' })).toHaveCount(0);
    await analystPage.goto(`/c/${company}/members`);
    await expect(analystPage.getByText('Only for owners and administrators')).toBeVisible();
    // another company's address is refused
    await analystPage.goto('/c/no-such-company/dashboard');
    await expect(analystPage.getByText('You cannot open this company')).toBeVisible();
  });

  test('suspending a member takes the company away at once, and reactivating gives it back', async () => {
    await analystPage.goto(`/c/${company}/dashboard`);
    await expect(analystPage.getByTestId('company-name')).toHaveText(companyName);

    await ownerPage.goto(`/c/${company}/members`);
    const row = ownerPage.getByRole('listitem').filter({ hasText: analyst });
    await row.getByRole('button', { name: 'Suspend' }).click();
    await expect(row.getByText('Suspended')).toBeVisible();

    await analystPage.reload();
    await expect(analystPage.getByText('You cannot open this company')).toBeVisible();

    await row.getByRole('button', { name: 'Reactivate' }).click();
    await expect(row.getByText('Active', { exact: true })).toBeVisible();
    await analystPage.reload();
    await expect(analystPage.getByTestId('company-name')).toHaveText(companyName);
  });

  test('an API token is shown once, works without a session, and stops when it is revoked', async ({ request }) => {
    await ownerPage.goto(`/c/${company}/api-tokens`);
    await ownerPage.getByLabel('Name', { exact: true }).fill('e2e export');
    await ownerPage.getByLabel('Role', { exact: true }).selectOption('VIEWER');
    await ownerPage.getByRole('button', { name: 'Create the token' }).click();
    const panel = ownerPage.getByRole('region', { name: 'New API token' });
    await expect(panel).toBeVisible();
    const secret = await panel.getByLabel('Secret').inputValue();
    expect(secret).toMatch(/^lis_/);

    // no session and no company header: the token is the credential, and it belongs to its own company
    expect((await request.get('/api/v1/assessments', { headers: { Authorization: `Bearer ${secret}` } })).status()).toBe(200);
    expect((await request.get('/api/v1/members', { headers: { Authorization: `Bearer ${secret}` } })).status()).toBe(403);

    await panel.getByRole('button', { name: 'I have saved it' }).click();
    await expect(ownerPage.locator('body')).not.toContainText(secret);
    await ownerPage.reload();
    await expect(ownerPage.locator('body')).not.toContainText(secret);

    const item = ownerPage.getByRole('listitem').filter({ hasText: 'e2e export' });
    await item.getByRole('button', { name: 'Revoke' }).click();
    await item.getByRole('button', { name: 'Yes' }).click();
    await expect(item.getByText('Revoked', { exact: true })).toBeVisible();
    expect((await request.get('/api/v1/assessments', { headers: { Authorization: `Bearer ${secret}` } })).status()).toBe(401);
  });

  test('the security audit of the company shows what happened', async () => {
    await ownerPage.goto(`/c/${company}/security-audit`);
    for (const event of ['Membership invited', 'Membership accepted', 'Membership suspended', 'Membership reactivated', 'Api token created', 'Api token revoked']) {
      await expect(ownerPage.getByRole('cell', { name: new RegExp(event) }).first()).toBeVisible();
    }
  });

  test('signing out ends the session, and a company address brings the person back after signing in', async () => {
    await ownerPage.getByRole('button', { name: 'Sign out' }).click();
    await expect(ownerPage).toHaveURL(/\/login$/);

    await ownerPage.goto(`/c/${company}/members`);
    await expect(ownerPage).toHaveURL(/\/login\?from=/);
    await ownerPage.getByLabel('Email address').fill(owner);
    await ownerPage.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await ownerPage.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(ownerPage).toHaveURL(new RegExp(`/c/${company}/members$`));
  });
});
