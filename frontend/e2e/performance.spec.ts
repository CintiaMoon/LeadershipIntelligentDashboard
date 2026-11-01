import { expect, test } from '@playwright/test';
import { asPersona } from './api';

// Budgets for a developer machine with everything on localhost: they catch regressions (an accidentally eager chart
// library, a slow query), not network behaviour. Real-user figures need a production-like environment.
const KB = 1024;

test.describe('performance budgets', () => {
  test('the login page downloads little JavaScript and paints quickly', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Leadership Intelligence', level: 1 })).toBeVisible();
    const { js, count, fcp } = await page.evaluate(() => {
      const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[];
      const scripts = resources.filter((r) => r.initiatorType === 'script' || r.name.endsWith('.js'));
      const paint = performance.getEntriesByName('first-contentful-paint')[0];
      return { js: scripts.reduce((n, r) => n + r.transferSize, 0), count: scripts.length, fcp: paint ? paint.startTime : 0 };
    });
    console.log(`PERF login: ${Math.round(js / KB)} kB JavaScript transferred in ${count} files, first contentful paint ${Math.round(fcp)} ms`);
    expect(js, 'JavaScript transferred for the login page').toBeLessThan(220 * KB);
    expect(fcp).toBeGreaterThan(0);
    expect(fcp, 'first contentful paint').toBeLessThan(2500);
  });

  test('the dashboard becomes usable quickly and stays within its JavaScript budget', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /^Administrator Full access/ }).click();
    await expect(page.getByTestId('lis-value')).toBeVisible();
    await page.waitForLoadState('networkidle');
    const metrics = await page.evaluate(
      () =>
        new Promise<{ js: number; lcp: number; calls: number }>((resolve) => {
          let lcp = 0;
          new PerformanceObserver((list) => {
            const last = list.getEntries().at(-1);
            if (last) lcp = last.startTime;
          }).observe({ type: 'largest-contentful-paint', buffered: true });
          setTimeout(() => {
            const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[];
            resolve({
              js: resources.filter((r) => r.name.endsWith('.js')).reduce((n, r) => n + r.transferSize, 0),
              lcp,
              calls: resources.filter((r) => r.name.includes('/api/v1/')).length,
            });
          }, 300);
        }),
    );
    console.log(`PERF dashboard: ${Math.round(metrics.js / KB)} kB JavaScript, largest contentful paint ${Math.round(metrics.lcp)} ms, ${metrics.calls} API calls`);
    expect(metrics.js, 'JavaScript transferred by the time the dashboard is complete').toBeLessThan(330 * KB);
    expect(metrics.lcp, 'largest contentful paint').toBeLessThan(4000);
    // one dashboard view costs a bounded number of requests
    expect(metrics.calls).toBeLessThanOrEqual(12);
  });

  test('the read endpoints behind the dashboard answer fast', async ({ request }) => {
    const admin = await asPersona(request, 'admin');
    const first = (await admin.json<{ items: { scoringRunId: string }[] }>('/scoring-runs?organizationId=org-2&limit=1')).items[0];
    const id = first?.scoringRunId as string;
    const paths = [
      '/organizations/org-2/dashboard',
      '/organizations/org-2/trends?limit=12',
      `/scoring-runs/${id}`,
      `/scoring-runs/${id}/patterns`,
      `/scoring-runs/${id}/penalties`,
      `/scoring-runs/${id}/catalog`,
      `/scoring-runs/${id}/recommendations`,
    ];
    const timings: number[] = [];
    for (let round = 0; round < 5; round++) {
      for (const path of paths) {
        const started = performance.now();
        const response = await admin.get(path);
        timings.push(performance.now() - started);
        expect(response.ok(), path).toBe(true);
      }
    }
    timings.sort((a, b) => a - b);
    const p95 = timings[Math.floor(timings.length * 0.95) - 1] as number;
    console.log(`PERF API: ${timings.length} calls, median ${Math.round(timings[Math.floor(timings.length / 2)] as number)} ms, p95 ${Math.round(p95)} ms`);
    expect(p95, 'p95 of read endpoint latency').toBeLessThan(500);
  });

  test('scoring a new assessment through the API is fast', async ({ request }) => {
    const leader = await asPersona(request, 'sandbox-leader');
    const started = performance.now();
    const response = await leader.post('/calculate', {
      assessment: { organizationId: 'org-sandbox', templateId: 'tpl-perf', templateVersion: '1', status: 'COMPLETED', completedAt: '2026-11-01T09:00:00Z' },
      inputs: [
        { questionId: 'Q01', answer: 3 },
        { questionId: 'Q02', answer: 'OFTEN' },
        { questionId: 'Q03', answer: true },
        { questionId: 'Q04', answer: 'shared ownership' },
        { questionId: 'Q05', answer: 60 },
      ],
    });
    const elapsed = performance.now() - started;
    console.log(`PERF calculate: ${Math.round(elapsed)} ms`);
    expect(response.ok()).toBe(true);
    expect(elapsed, 'time to score and store one assessment').toBeLessThan(1500);
  });
});
