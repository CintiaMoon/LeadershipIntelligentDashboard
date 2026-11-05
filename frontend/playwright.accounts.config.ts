import { defineConfig } from '@playwright/test';

// End-to-end tests of the application's own accounts (decisions D65 to D74): registration, confirmation, sign-in, invitations,
// companies in the address, members, API tokens and the security audit. They run against the real stack
// (`docker compose up -d --build`): the web UI on :3000, the API on :8080 and Mailpit on :8025, where the emails
// with the links are read. They need no seeded data and create their own people and companies.
export default defineConfig({
  testDir: 'e2e-accounts',
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  timeout: 60_000,
  use: { baseURL: process.env.E2E_ACCOUNTS_URL ?? 'http://localhost:3000', trace: 'retain-on-failure' },
});
