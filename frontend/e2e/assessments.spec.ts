import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { asPersona } from './api';
import { tokenFor } from './seed';

const API = process.env.E2E_API_URL ?? 'http://localhost:8080';
// a scratch organization of its own: the other specs rely on org-1 and org-2 holding only the seeded series
const TEAM = 'E2E Alpha';
const PEOPLE = [1, 2, 3, 4, 5].map((n) => ({ name: `Respondent ${n}`, email: `respondent${n}@demo.test`, persona: `respondent-${n}` }));

async function signIn(page: Page, persona: RegExp, landing: RegExp) {
  await page.goto('/login');
  await page.getByRole('button', { name: persona }).click();
  await page.waitForURL(landing);
}

const nav = (page: Page) => page.getByRole('navigation', { name: 'Main' });

/** Ratings of the questionnaire below, one per dimension, answered by every respondent with the same low value. */
const QUESTIONS = [
  { text: 'How sustainable is the team pace?', dimension: 'TEAM_HEALTH' },
  { text: 'How predictable is delivery?', dimension: 'DELIVERY_STABILITY' },
  { text: 'How well is technical risk controlled?', dimension: 'TECHNICAL_RISK' },
  { text: 'How manageable is the load on leaders?', dimension: 'LEADERSHIP_LOAD' },
];

test.describe.configure({ mode: 'serial' });

test.describe('questionnaires, people, runs and the respondent flow', () => {
  const stamp = Date.now();
  const questionnaireName = `E2E pulse ${stamp}`;
  const runName = `E2E run ${stamp}`;
  let runId = '';

  test('an administrator builds a questionnaire, is told what is missing, and publishes it', async ({ page }) => {
    test.setTimeout(120_000);
    await signIn(page, /^Assessments administrator/, /\/dashboard$/);
    await nav(page).getByRole('link', { name: 'Questionnaires' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Questionnaires' })).toBeVisible();
    await page.getByRole('link', { name: 'New questionnaire' }).first().click();
    await page.getByLabel('Name', { exact: true }).fill(questionnaireName);
    await page.getByRole('button', { name: 'Create questionnaire' }).click();
    await expect(page.getByRole('heading', { level: 1, name: questionnaireName })).toBeVisible();

    // four ratings, none mapped to a dimension yet, and one free-text question that is never scored
    for (const q of QUESTIONS) {
      await page.getByRole('group', { name: 'Add a question' }).getByRole('button', { name: /Rating/ }).click();
      const n = QUESTIONS.indexOf(q) + 1;
      await page.getByLabel(`Question ${n} text`).fill(q.text);
    }
    await page.getByRole('group', { name: 'Add a question' }).getByRole('button', { name: /Free text/ }).click();
    await page.getByLabel('Question 5 text').fill('Anything else you want to say?');
    const first = page.getByLabel('Question 1 text').locator('xpath=ancestor::li[1]');
    await first.getByText('Required', { exact: true }).click();
    await expect(first.getByRole('switch', { name: 'Required' })).toBeChecked();

    await page.getByRole('button', { name: 'Publish' }).click();
    const alert = page.getByRole('alert').filter({ hasText: 'has no scored question' });
    await expect(alert).toContainText('dimension TEAM_HEALTH has no scored question');
    await expect(alert).toContainText('dimension LEADERSHIP_LOAD has no scored question');

    for (const [i, q] of QUESTIONS.entries()) {
      await page.getByLabel(`Question ${i + 1} dimension`).selectOption(q.dimension);
    }
    // a rule: low pace detects a pattern, lowers Team Health and recommends an action
    await page.getByRole('button', { name: 'Add rule' }).click();
    await page.getByLabel('Rule 1 name').fill('Unsustainable pace');
    await page.getByLabel('Rule 1 description').fill('The team reports an unsustainable pace.');
    await page.getByLabel('Detecting it lowers the score').check();
    await page.getByLabel('Recommend an action').check();
    await page.getByLabel('Recommendation title').fill('Review the team workload');
    await page.getByLabel('Recommendation description').fill('Compare the plan with what the team can do.');

    await page.getByRole('button', { name: 'Publish' }).click();
    await expect(page.getByText(/This version is published and never changes/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Publish' })).toHaveCount(0);

    // the library shows it as published
    await page.goto('/questionnaires');
    const row = page.getByRole('row', { name: new RegExp(questionnaireName) });
    await expect(row).toContainText('Published');
    await expect(row).toContainText('v1 published');
  });

  test('an administrator sets up a team and its people once', async ({ page }) => {
    test.setTimeout(120_000);
    await signIn(page, /^Assessments administrator/, /\/dashboard$/);
    await nav(page).getByRole('link', { name: 'People' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'People' })).toBeVisible();
    await expect(page.getByText(/Reading this list is recorded/)).toBeVisible();
    // the roster persists between runs of this suite, so it is only built when it is not there yet
    await page.waitForLoadState('networkidle');
    if ((await page.getByRole('region', { name: `Team ${TEAM}` }).count()) === 0) {
      await page.getByLabel('Team name').fill(TEAM);
      await page.getByRole('button', { name: 'Add team' }).click();
      await expect(page.getByRole('region', { name: `Team ${TEAM}` })).toBeVisible();
    }
    const card = page.getByRole('region', { name: `Team ${TEAM}` });
    for (const person of PEOPLE) {
      if ((await card.getByText(person.email, { exact: true }).count()) > 0) continue;
      await card.getByRole('button', { name: 'Add person' }).first().click();
      await card.getByLabel('Name', { exact: true }).fill(person.name);
      await card.getByLabel('E-mail address').fill(person.email);
      await card.getByRole('button', { name: 'Add person' }).last().click();
      await expect(card.getByText(person.email, { exact: true })).toBeVisible();
    }
    await expect(card.getByText('5 active people')).toBeVisible();
  });

  test('an administrator starts a run and sees nobody has started', async ({ page }) => {
    test.setTimeout(60_000);
    await signIn(page, /^Assessments administrator/, /\/dashboard$/);
    await nav(page).getByRole('link', { name: 'Assessment runs' }).click();
    await page.getByRole('link', { name: 'Start a run' }).click();
    await page.getByLabel('Name', { exact: true }).fill(runName);
    await page.getByLabel('Questionnaire').selectOption({ label: `${questionnaireName} (v1)` });
    await page.getByRole('checkbox', { name: new RegExp(TEAM) }).check();
    await page.getByRole('button', { name: 'Start run' }).click();
    await expect(page.getByRole('heading', { level: 1, name: runName })).toBeVisible();
    runId = page.url().split('/').pop() as string;
    expect(runId).toMatch(/^[0-9a-f-]{36}$/);
    await expect(page.getByText(/0 of 5 finished/)).toBeVisible();
    await expect(page.getByRole('note').filter({ hasText: 'work out who is missing' })).toBeVisible();
    const people = page.getByRole('table', { name: /People asked to answer/ });
    await expect(people.getByRole('row')).toHaveCount(6);
    await expect(people.getByText('Not started').first()).toBeVisible();
    // there is no way to read an answer from this page
    await expect(page.getByText(/answers/i).first()).toBeVisible();
  });

  test('a respondent sees only their own assessments, answers one with a draft in between, and submits', async ({ page }) => {
    test.setTimeout(120_000);
    await signIn(page, /^Respondent Answers the questionnaires/, /\/respond$/);
    // nothing but their own assessments is offered, and the manager screens say they are not for them
    await expect(nav(page).getByRole('link')).toHaveText(['My assessments']);
    await expect(page.getByLabel('Organization')).toHaveCount(0);
    await page.goto('/questionnaires');
    await expect(page.getByRole('heading', { name: 'Only for owners and administrators' })).toBeVisible();
    await page.goto('/respond');
    await page.getByRole('link', { name: `Start ${runName}` }).click();
    await expect(page.getByRole('heading', { level: 1, name: runName })).toBeVisible();
    await expect(page.getByRole('note')).toContainText('never with your name');

    // the required question blocks submitting
    await page.getByRole('button', { name: 'Submit answers' }).click();
    await expect(page.getByText('1 required question needs an answer before you can submit.')).toBeVisible();

    const answer = async (text: string, value: string) => page.getByRole('group', { name: new RegExp(text) }).locator('label', { hasText: new RegExp(`^${value}$`) }).click();
    await answer(QUESTIONS[0]!.text, '2');
    await answer(QUESTIONS[1]!.text, '2');
    await page.getByRole('button', { name: 'Save draft' }).click();
    await expect(page.getByText(/Draft saved/)).toBeVisible();

    // continue later: the draft is back after a reload
    await page.reload();
    await expect(page.getByRole('group', { name: new RegExp(QUESTIONS[0]!.text) }).getByRole('radio', { name: '2' })).toBeChecked();
    await answer(QUESTIONS[2]!.text, '2');
    await answer(QUESTIONS[3]!.text, '2');
    await page.getByRole('textbox', { name: 'Anything else you want to say?' }).fill('More focus time, please.');
    await page.getByRole('button', { name: 'Submit answers' }).click();
    await expect(page.getByText('Thank you, your answers are submitted')).toBeVisible();
    await expect(page.getByRole('radio')).toHaveCount(0);
    await page.goto('/respond');
    await expect(page.getByRole('listitem').filter({ hasText: runName }).getByText('Submitted')).toBeVisible();
  });

  test('the other respondents answer, and the administrator closes the run and reaches the result', async ({ page, request }) => {
    test.setTimeout(120_000);
    const authorized = async (persona: string) => ({ Authorization: `Bearer ${await tokenFor(persona)}` });
    for (const person of PEOPLE.slice(1)) {
      const headers = await authorized(person.persona);
      const saved = await request.put(`${API}/api/v1/respond/runs/${runId}/draft`, {
        headers,
        data: {
          answers: [
            { questionId: 'Q01', answer: 2 },
            { questionId: 'Q02', answer: 2 },
            { questionId: 'Q03', answer: 2 },
            { questionId: 'Q04', answer: 2 },
          ],
        },
      });
      expect(saved.ok(), `${person.persona} saves a draft`).toBe(true);
      const submitted = await request.post(`${API}/api/v1/respond/runs/${runId}/submit`, { headers });
      expect(submitted.ok(), `${person.persona} submits`).toBe(true);
    }

    await signIn(page, /^Assessments administrator/, /\/dashboard$/);
    await page.goto(`/assessment-runs/${runId}`);
    await expect(page.getByText(/5 of 5 finished/)).toBeVisible();
    const people = page.getByRole('table', { name: /People asked to answer/ });
    await expect(people.getByText('Submitted')).toHaveCount(5);

    await page.getByRole('button', { name: 'Close and score' }).click();
    await expect(page.getByText('0 of 5 people have not finished. Close the run anyway?')).toBeVisible();
    await page.getByRole('button', { name: 'Close the run' }).click();
    await expect(page.getByRole('heading', { name: 'Result' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Close and score' })).toHaveCount(0);

    // the answers were scored: the organization result exists and its rule detected the pattern it was written for
    const admin = await asPersona(request, 'assess-admin');
    const detail = await admin.json<{ run: { scoringRunId: string; status: string } }>(`/runs/${runId}`);
    expect(detail.run.status).toBe('CLOSED');
    const scored = await admin.json<{ scoreStatus: string; dimensions: Record<string, { rawScore?: number }>; patterns: { patternId: string; status: string }[] }>(
      `/scoring-runs/${detail.run.scoringRunId}`,
    );
    expect(scored.scoreStatus).toBe('VALID');
    expect(scored.dimensions['TEAM_HEALTH']?.rawScore).toBe(25);
    expect(scored.patterns.find((p) => p.patternId === 'X-1')?.status).toBe('MATCHED');

    await page.getByRole('link', { name: 'How the score was calculated' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'How the Leadership Intelligence Score was calculated' })).toBeVisible();
  });

  test('no answer is linked to a person, and a respondent cannot reach what is not theirs', async ({ request }) => {
    const admin = await asPersona(request, 'assess-admin');
    // the participant list says who finished and carries no answer
    const detail = await admin.json<{ participants: Record<string, unknown>[] }>(`/runs/${runId}`);
    expect(detail.participants).toHaveLength(5);
    for (const p of detail.participants) expect(Object.keys(p).sort()).toEqual(['email', 'name', 'status', 'teamId']);
    // a respondent token is refused everywhere outside the respondent flow
    const headers = { Authorization: `Bearer ${await tokenFor('respondent-2')}` };
    for (const path of ['/runs', '/teams', '/people', '/questionnaires', '/scoring-runs']) {
      const response = await request.get(`${API}/api/v1${path}`, { headers });
      expect(response.status(), path).toBe(403);
    }
    // and a person who is not on the roster has nothing to answer
    const stranger = await request.get(`${API}/api/v1/respond/runs/${runId}`, { headers: { Authorization: `Bearer ${await tokenFor('respondent-6')}` } });
    expect(stranger.status()).toBe(404);
  });

  for (const theme of ['dark', 'light'] as const) {
    test(`the new screens have no automated WCAG 2.2 AA violations and fit a phone (${theme} theme)`, async ({ page, request, browser }) => {
      test.setTimeout(180_000);
      const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];
      const scan = async (what: string, on: Page = page) => {
        const results = await new AxeBuilder({ page: on }).withTags(TAGS).analyze();
        const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
        expect(summary, `${what} (${theme}) has accessibility violations`).toEqual([]);
      };
      const noOverflow = async (path: string, on: Page = page) => {
        const overflow = await on.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow, `${path} scrolls horizontally on a phone`).toBeLessThanOrEqual(1);
      };
      if (theme === 'light') await page.addInitScript(() => window.localStorage.setItem('lis.theme', 'light'));

      // a second, open run so that the respondent form can be scanned
      const admin = await asPersona(request, 'assess-admin');
      const questionnaires = await admin.json<{ id: string; name: string }[]>('/questionnaires');
      const questionnaireId = questionnaires.find((q) => q.name === questionnaireName)?.id as string;
      const teams = await admin.json<{ id: string; name: string }[]>('/teams?organizationId=org-assess');
      const teamId = teams.find((t) => t.name === TEAM)?.id as string;
      const created = await admin.post('/runs', { name: `${runName} scan ${theme}`, organizationId: 'org-assess', questionnaireId, teamIds: [teamId] });
      expect(created.ok()).toBe(true);
      const openRun = ((await created.json()) as { run: { id: string } }).run.id;

      await signIn(page, /^Assessments administrator/, /\/dashboard$/);
      const screens: [string, string, string | RegExp][] = [
        ['the library', '/questionnaires', 'Questionnaires'],
        ['a new questionnaire', '/questionnaires/new', 'New questionnaire'],
        ['the people', '/people', 'People'],
        ['the runs', '/assessment-runs', 'Assessment runs'],
        ['starting a run', '/assessment-runs/new', 'Start a run'],
        ['an open run', `/assessment-runs/${openRun}`, `${runName} scan ${theme}`],
        ['a closed run', `/assessment-runs/${runId}`, runName],
      ];
      for (const [what, path, heading] of screens) {
        await page.goto(path);
        await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
        await page.waitForLoadState('networkidle');
        await scan(what);
      }
      // the builder, with a questionnaire that has choices, rules and every kind of control
      await page.goto('/questionnaires');
      await page.getByRole('row', { name: new RegExp(questionnaireName) }).getByRole('link', { name: 'Open' }).click();
      await expect(page.getByRole('heading', { level: 1, name: questionnaireName })).toBeVisible();
      // the first scan finds the published version; a later one finds the draft that scan opened
      const editAsDraft = page.getByRole('button', { name: 'Edit as a new draft' });
      if ((await editAsDraft.count()) > 0) {
        await scan('the builder (published, read only)');
        await editAsDraft.click();
      }
      await expect(page.getByRole('button', { name: 'Publish' })).toBeVisible();
      await page.getByRole('group', { name: 'Add a question' }).getByRole('button', { name: /Single choice/ }).click();
      await page.getByRole('group', { name: 'Add a question' }).getByRole('button', { name: /Likert/ }).click();
      await page.getByRole('group', { name: 'Add a question' }).getByRole('button', { name: /Numeric/ }).click();
      await page.getByRole('group', { name: 'Add a question' }).getByRole('button', { name: /Yes \/ No/ }).click();
      await page.getByRole('group', { name: 'Add a question' }).getByRole('button', { name: /Multiple choice/ }).click();
      await page.getByRole('button', { name: 'Add rule' }).click();
      await page.getByLabel('Detecting it lowers the score').last().check();
      await scan('the builder (draft with every kind of question)');
      await page.setViewportSize({ width: 390, height: 844 });
      await noOverflow('the builder');

      for (const path of ['/questionnaires', '/people', '/assessment-runs', `/assessment-runs/${runId}`]) {
        await page.goto(path);
        await page.waitForLoadState('networkidle');
        await noOverflow(path);
      }

      // the respondent screens, in a browser session of their own
      const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
      const rp = await context.newPage();
      if (theme === 'light') await rp.addInitScript(() => window.localStorage.setItem('lis.theme', 'light'));
      await signIn(rp, /^Respondent Answers the questionnaires/, /\/respond$/);
      await expect(rp.getByRole('heading', { level: 1, name: 'My assessments' })).toBeVisible();
      await rp.waitForLoadState('networkidle');
      await scan('my assessments', rp);
      await rp.goto(`/respond/${openRun}`);
      await expect(rp.getByRole('heading', { level: 1, name: `${runName} scan ${theme}` })).toBeVisible();
      await scan('the questionnaire form', rp);
      await rp.getByRole('button', { name: 'Submit answers' }).click();
      await expect(rp.getByText('1 required question needs an answer before you can submit.')).toBeVisible();
      await scan('the questionnaire form with a missing answer', rp);
      await rp.setViewportSize({ width: 390, height: 844 });
      await noOverflow('the questionnaire form', rp);
      await context.close();
    });
  }
});
