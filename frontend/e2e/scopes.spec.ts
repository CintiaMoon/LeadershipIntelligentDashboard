import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { asPersona } from './api';

// Answers from several people (decision D45) in the scratch organization, so the seeded demo series stay untouched.
const TEMPLATE = 'tpl-teams';

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByRole('button', { name: /^Sandbox administrator/ }).click();
  await expect(page.getByTestId('lis-value')).toBeVisible();
}

function member(team: string, index: number) {
  return [
    { questionId: 'Q01', answer: 1 + (index % 5) },
    { questionId: 'Q02', answer: 'OFTEN' },
    { questionId: 'Q03', answer: index % 2 === 0 },
    { questionId: 'Q04', answer: 'a steady team' },
    { questionId: 'Q05', answer: 60 + 5 * index },
  ].map((a) => ({ ...a, scope: 'MEMBER', teamId: team }));
}

async function scoreTeams(request: Parameters<typeof asPersona>[0]) {
  const leader = await asPersona(request, 'sandbox-leader');
  const responses = [
    ...Array.from({ length: 6 }, (_, i) => member('alpha', i)),
    ...Array.from({ length: 5 }, (_, i) => member('beta', i)),
    ...Array.from({ length: 3 }, (_, i) => member('tiny', i)),
  ].flat();
  const response = await leader.post('/calculate', {
    assessment: { organizationId: 'org-sandbox', templateId: TEMPLATE, templateVersion: '1', status: 'COMPLETED', completedAt: '2026-11-01T09:00:00Z' },
    responses,
  });
  expect(response.ok()).toBe(true);
  return (await response.json()) as any;
}

const two = (value: number) => value.toFixed(2);

async function expectNoViolations(page: Page, what: string) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  expect(summary, `${what} has accessibility violations`).toEqual([]);
}

test.describe('answers from several people', () => {
  test('the dashboard switches between the organization and its teams, and a small team gets no score', async ({ page, request }) => {
    const result = await scoreTeams(request);
    const teams = Object.fromEntries(result.teamRuns.map((t: any) => [t.teamId, t]));
    expect(Object.keys(teams)).toEqual(['alpha', 'beta', 'tiny']);
    expect(teams.tiny.scoreStatus).toBe('INSUFFICIENT_DATA');
    expect(teams.tiny.leadershipIntelligenceScore).toBeUndefined();

    await signIn(page);
    await page.goto(`/dashboard?template=${TEMPLATE}`);
    await expect(page.getByTestId('lis-value')).toHaveText(two(result.leadershipIntelligenceScore));

    // how the answers were combined: counts and method, never what a person answered
    const panel = page.getByRole('heading', { name: 'How the answers were combined' });
    await expect(panel).toBeVisible();
    const combined = page.getByRole('table', { name: 'How the answers of each question were combined' });
    await expect(combined.getByRole('row').nth(1)).toContainText('Individual responses');
    await expect(combined.getByRole('row').nth(1)).toContainText('Median');
    await expect(combined.getByRole('row').nth(1)).toContainText('14');

    const scope = page.getByRole('combobox', { name: 'Scope' });
    await expect(scope.getByRole('option')).toHaveText(['Whole organization', 'Team alpha', 'Team beta', 'Team tiny (not enough responses)']);

    // a team shows its own score; organization-only history is not offered
    await scope.selectOption('alpha');
    await expect(page).toHaveURL(/team=alpha/);
    await expect(page.getByTestId('lis-value')).toHaveText(two(teams.alpha.leadershipIntelligenceScore));
    await expect(page.getByText(/Teams are never ranked against each other/)).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Historical trends' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'How was this calculated?' })).toHaveAttribute('href', `/runs/${teams.alpha.scoringRunId}/explanation`);

    await scope.selectOption('beta');
    await expect(page.getByTestId('lis-value')).toHaveText(two(teams.beta.leadershipIntelligenceScore));

    // a team below the minimum: no number, and the reason
    await scope.selectOption('tiny');
    await expect(page.getByTestId('lis-value')).not.toHaveText(/\d/);
    await expect(page.getByRole('note').filter({ hasText: 'fewer than 5 responses' })).toContainText('Groups this small are never shown');

    await scope.selectOption('');
    await expect(page.getByTestId('lis-value')).toHaveText(two(result.leadershipIntelligenceScore));
    await expect(page.getByRole('heading', { name: 'Historical trends' })).toBeVisible();
  });

  test('a team run explains itself and its answers appear only as sorted groups, small groups as a count', async ({ page, request }) => {
    const result = await scoreTeams(request);
    const alpha = result.teamRuns.find((t: any) => t.teamId === 'alpha');
    const tiny = result.teamRuns.find((t: any) => t.teamId === 'tiny');

    await signIn(page);
    await page.goto(`/runs/${alpha.scoringRunId}/explanation`);
    await expect(page.getByText(/Team alpha/).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'How the answers were combined' })).toBeVisible();

    await page.goto(`/runs/${alpha.scoringRunId}/dimensions/TEAM_HEALTH`);
    const table = await page.getByRole('table', { name: 'Contributions to Team Health' });
    await table.getByRole('button', { name: 'Show answer' }).first().click();
    await expect(page.getByText(/responses, in sorted order and not in the order given/)).toBeVisible();

    // the API never lists members one by one, and hides the values of a group that is too small
    const admin = await asPersona(request, 'sandbox-admin');
    const answers = await admin.json<{ answers: any[] }>(`/scoring-runs/${tiny.scoringRunId}/answers`);
    const members = answers.answers.filter((a) => a.scope === 'MEMBER');
    expect(members.length).toBeGreaterThan(0);
    for (const group of members) {
      expect(group.suppressed).toBe(true);
      expect(group.count).toBe(3);
      expect(group.values).toEqual([]);
    }
    const org = await admin.json<{ answers: any[] }>(`/scoring-runs/${result.scoringRunId}/answers`);
    const q01 = org.answers.find((a) => a.questionId === 'Q01' && a.scope === 'MEMBER');
    expect(q01.count).toBe(14);
    expect(q01.values).toEqual([...q01.values].sort((a: number, b: number) => a - b));
  });

  test('the new screens have no automated WCAG 2.2 AA violations and fit a phone', async ({ page, request }) => {
    const result = await scoreTeams(request);
    const alpha = result.teamRuns.find((t: any) => t.teamId === 'alpha');
    await signIn(page);
    await page.goto(`/dashboard?template=${TEMPLATE}`);
    await expect(page.getByRole('heading', { name: 'How the answers were combined' })).toBeVisible();
    await page.getByText('View the data').first().click();
    await expectNoViolations(page, 'organization dashboard with answers from several people');

    await page.getByRole('combobox', { name: 'Scope' }).selectOption('tiny');
    await expect(page.getByRole('note').filter({ hasText: 'fewer than 5 responses' })).toBeVisible();
    await expectNoViolations(page, 'dashboard of a team with too few responses');

    await page.goto(`/runs/${alpha.scoringRunId}/explanation`);
    await expect(page.getByRole('heading', { name: 'How the answers were combined' })).toBeVisible();
    await page.evaluate(() => document.querySelectorAll('details').forEach((d) => (d.open = true)));
    await expectNoViolations(page, 'explanation of a team run');

    await page.setViewportSize({ width: 390, height: 800 });
    for (const path of [`/dashboard?template=${TEMPLATE}&team=alpha`, `/dashboard?template=${TEMPLATE}&team=tiny`]) {
      await page.goto(path);
      await expect(page.getByTestId('lis-value')).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${path} scrolls horizontally`).toBeLessThanOrEqual(0);
    }
  });

  test('the organization history and trends never include a team run', async ({ request }) => {
    const result = await scoreTeams(request);
    const admin = await asPersona(request, 'sandbox-admin');
    const history = await admin.json<{ items: any[] }>('/scoring-runs?organizationId=org-sandbox&limit=50');
    const ids = new Set(history.items.map((i) => i.scoringRunId));
    expect(ids.has(result.scoringRunId)).toBe(true);
    for (const team of result.teamRuns) {
      if (team.scoringRunId) expect(ids.has(team.scoringRunId)).toBe(false);
    }
    const trends = await admin.json<{ points: any[] }>(`/organizations/org-sandbox/trends?templateId=${TEMPLATE}`);
    expect(trends.points.every((p) => p.run.scoringRunId !== result.teamRuns[0].scoringRunId)).toBe(true);
    const base = result.teamRuns[0].scoringRunId;
    const compare = await admin.get(`/scoring-runs/compare?baseRunId=${base}&runId=${result.scoringRunId}`);
    expect(compare.status()).toBe(404);
  });
});
