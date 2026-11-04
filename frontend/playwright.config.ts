import { defineConfig } from '@playwright/test';

// End-to-end tests run against the real stack: `docker compose up -d --build` (web UI on :3000, API on :8080).
export default defineConfig({
  testDir: 'e2e',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  // in CI the runner is slower and shared: one retry for the timing budgets, two workers so the tests do not starve each other
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['list'], ['github']] : [['list']],
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000', trace: 'retain-on-failure' },
});
