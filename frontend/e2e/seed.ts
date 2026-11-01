// Deterministic demo data for the end-to-end tests, created through the real API as an engineering leader.
// Idempotent: a series is only created when its organization has none yet. Run it alone with `npm run e2e:seed`.
const AUTH = process.env.E2E_AUTH_URL ?? 'http://localhost:8081/default';
const API = process.env.E2E_API_URL ?? 'http://localhost:8080';

interface Step {
  templateVersion: string;
  /** answer to Q01 (team sustainability, 1-5): 4 -> 75, 5 -> 100, 2 -> 25 on the normalized scale */
  sustainability: number;
}

/**
 * org-1: healthy, healthier, then burnout risk (comparable, same configuration and template version).
 * org-2: the same three, then the burnout answers again on template version 2, which is not comparable with the run
 * before it. With the dev sample configuration the scores are 81.25, 87.50, 67.00 (and 67.00 on version 2): the key-person pattern deducts 5 from Team Health in the first two runs and is suppressed by the larger burnout deduction in the last.
 */
export const SERIES: Record<string, Step[]> = {
  'org-1': [
    { templateVersion: '1', sustainability: 4 },
    { templateVersion: '1', sustainability: 5 },
    { templateVersion: '1', sustainability: 2 },
  ],
  'org-2': [
    { templateVersion: '1', sustainability: 4 },
    { templateVersion: '1', sustainability: 5 },
    { templateVersion: '1', sustainability: 2 },
    { templateVersion: '2', sustainability: 2 },
  ],
};

export async function tokenFor(persona: string): Promise<string> {
  const response = await fetch(`${AUTH}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: persona, client_secret: 'secret' }),
  });
  if (!response.ok) throw new Error(`token request failed: ${response.status}`);
  return ((await response.json()) as { access_token: string }).access_token;
}

export const answersFor = (sustainability: number) => [
  { questionId: 'Q01', answer: sustainability },
  { questionId: 'Q02', answer: 'OFTEN' },
  { questionId: 'Q03', answer: true },
  { questionId: 'Q04', answer: 'only one key person' },
  { questionId: 'Q05', answer: 80 },
];

export async function seed(): Promise<{ created: Record<string, number> }> {
  const token = await tokenFor('leader');
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  const created: Record<string, number> = {};
  for (const [organizationId, steps] of Object.entries(SERIES)) {
    const existing = await fetch(`${API}/api/v1/scoring-runs?organizationId=${organizationId}&limit=1`, { headers });
    if (!existing.ok) throw new Error(`cannot read ${organizationId}: ${existing.status}`);
    if (((await existing.json()) as { items: unknown[] }).items.length > 0) {
      created[organizationId] = 0;
      continue;
    }
    for (const step of steps) {
      const response = await fetch(`${API}/api/v1/calculate`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          assessment: { organizationId, templateId: 'tpl-1', templateVersion: step.templateVersion, status: 'COMPLETED', completedAt: '2026-11-01T09:00:00Z' },
          inputs: answersFor(step.sustainability),
        }),
      });
      if (!response.ok) throw new Error(`seeding ${organizationId} failed: ${response.status}`);
      // distinct timestamps keep the trend labels distinct
      await new Promise((resolve) => setTimeout(resolve, 1100));
    }
    created[organizationId] = steps.length;
  }
  return { created };
}

// run directly (`node e2e/seed.ts`), not when imported by the Playwright global setup
if (process.argv[1]?.endsWith('seed.ts')) {
  seed().then((r) => console.log('seed:', JSON.stringify(r)), (e) => { console.error(e); process.exit(1); });
}
