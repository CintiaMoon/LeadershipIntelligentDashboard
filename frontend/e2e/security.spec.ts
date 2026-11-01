import { expect, test, type Page } from '@playwright/test';
import { asPersona, runsOf } from './api';

async function signIn(page: Page, persona: RegExp) {
  await page.goto('/login');
  await page.getByRole('button', { name: persona }).click();
  await page.waitForURL(/\/dashboard$/);
}

test.describe('roles, organizations and tenant isolation', () => {
  test('another tenant sees none of the data, through the interface and through the API', async ({ page, request }) => {
    const admin = await asPersona(request, 'admin');
    const run = (await runsOf(admin, 'org-2'))[2];
    const other = await asPersona(request, 'other-admin');

    expect((await other.get(`/scoring-runs/${run.scoringRunId}`)).status()).toBe(404);
    expect((await other.get(`/scoring-runs/${run.scoringRunId}/explanation`)).status()).toBe(404);
    expect((await other.get(`/scoring-runs/${run.scoringRunId}/answers`)).status()).toBe(404);
    expect((await other.get(`/assessments/${run.assessmentId}`)).status()).toBe(404);
    expect((await other.get(`/scoring-runs/compare?baseRunId=${run.scoringRunId}&runId=${run.scoringRunId}`)).status()).toBe(404);
    expect((await (await other.get('/scoring-runs?organizationId=org-1')).json()).items).toEqual([]);
    expect((await (await other.get('/assessments')).json()).items).toEqual([]);

    await signIn(page, /^Administrator of another tenant/);
    await expect(page.getByText('No scored assessments yet')).toBeVisible();
    await page.goto(`/runs/${run.scoringRunId}/explanation`);
    await expect(page.getByRole('alert')).toContainText('Not found');
  });

  test('a user limited to one organization cannot reach the other, anywhere', async ({ page, request }) => {
    const limited = await asPersona(request, 'org1-viewer');
    expect((await limited.get('/organizations/org-2/dashboard')).status()).toBe(403);
    expect((await limited.get('/scoring-runs?organizationId=org-2')).status()).toBe(403);
    expect((await limited.get('/organizations/org-2/trends')).status()).toBe(403);
    expect((await limited.get('/organizations/org-1/dashboard')).status()).toBe(200);
    const admin = await asPersona(request, 'admin');
    const foreign = (await runsOf(admin, 'org-2'))[0];
    expect((await limited.get(`/scoring-runs/${foreign.scoringRunId}`)).status()).toBe(403);

    await signIn(page, /^Viewer, one organization/);
    await expect(page.getByRole('banner').getByLabel('Organization').locator('option')).toHaveText(['org-1']);
  });

  test('roles: who may score and who may read evidence, enforced by the backend', async ({ request }) => {
    const admin = await asPersona(request, 'admin');
    const run = (await runsOf(admin, 'org-2'))[2];
    const expectations: Record<string, { answers: number; score: number }> = {
      owner: { answers: 200, score: 200 },
      admin: { answers: 200, score: 200 },
      analyst: { answers: 200, score: 403 },
      leader: { answers: 403, score: 200 },
      viewer: { answers: 403, score: 403 },
    };
    for (const [persona, expected] of Object.entries(expectations)) {
      const p = await asPersona(request, persona);
      expect((await p.get(`/scoring-runs/${run.scoringRunId}/answers`)).status(), `${persona} answers`).toBe(expected.answers);
      expect((await p.post(`/assessments/${run.assessmentId}/score`)).status(), `${persona} score`).toBe(expected.score);
      expect((await p.get(`/scoring-runs/${run.scoringRunId}`)).status(), `${persona} reads results`).toBe(200);
    }
    // choosing a configuration version is for owners and administrators only
    const leader = await asPersona(request, 'leader');
    expect((await leader.post(`/assessments/${run.assessmentId}/score/recalculate`, { configurationVersion: '00000000-0000-0000-0000-000000000000' })).status()).toBe(403);
  });

  test('the viewer receives findings and scores but no per-question values', async ({ request }) => {
    const admin = await asPersona(request, 'admin');
    const viewer = await asPersona(request, 'viewer');
    const run = (await runsOf(admin, 'org-2'))[2];
    const explanation = (await viewer.json(`/scoring-runs/${run.scoringRunId}/explanation`)) as any;
    expect(explanation.redacted).toBe(true);
    const stages = explanation.trace.steps.map((s: any) => s.stage);
    expect(stages).not.toContain('NORMALIZATION');
    expect(stages).not.toContain('SIGNALS');
    const patterns = (await viewer.json(`/scoring-runs/${run.scoringRunId}/patterns`)) as any;
    expect(patterns.redacted).toBe(true);
    for (const p of patterns.patterns) for (const e of p.evidence ?? []) expect(e.numericValue, 'no evidence values for a viewer').toBeUndefined();
    const serialized = JSON.stringify(explanation) + JSON.stringify(patterns);
    expect(serialized).not.toContain('only one key person');
  });

  test('an anonymous caller gets nothing', async ({ request }) => {
    const response = await request.get('http://localhost:8080/api/v1/scoring-runs');
    expect(response.status()).toBe(401);
    const body = await response.json();
    expect(JSON.stringify(body)).not.toMatch(/Exception|at com\.|stack/i);
  });

  test('the interface offers only what the role may do', async ({ page }) => {
    await signIn(page, /^Viewer\b(?!,)/);
    await page.getByRole('link', { name: 'Detailed analysis: Team Health' }).click();
    await expect(page.getByRole('button', { name: 'Show answer' })).toHaveCount(0);
    await page.goto('/dashboard');
    await expect(page.getByRole('link', { name: 'How was this calculated?' })).toBeVisible();
  });
});
