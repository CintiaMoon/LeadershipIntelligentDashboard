import { expect, test } from '@playwright/test';
import { asPersona, runsOf } from './api';
import { checkDeterminism, checkRunInvariants } from './invariants';
import { answersFor } from './seed';

test.describe('scoring invariants on the running system', () => {
  test('every seeded run satisfies the mathematical invariants', async ({ request }) => {
    const admin = await asPersona(request, 'admin');
    for (const organization of ['org-1', 'org-2']) {
      const runs = await runsOf(admin, organization);
      expect(runs.length).toBeGreaterThanOrEqual(3);
      for (const run of runs) await checkRunInvariants(admin, run.scoringRunId);
    }
  });

  test('the burnout run shows the contract in numbers: one applied, one suppressed, adjusted below raw only where applied', async ({ request }) => {
    const admin = await asPersona(request, 'admin');
    const runs = await runsOf(admin, 'org-2');
    const { run, penalties, effectiveByTarget } = await checkRunInvariants(admin, runs[2].scoringRunId);
    const byId = Object.fromEntries(penalties.map((p) => [p.penaltyId, p]));
    expect(byId['TH-P01']).toMatchObject({ status: 'APPLIED', candidateDeduction: 12, effectiveDeduction: 12, suppressedAmount: 0 });
    expect(byId['TH-P02']).toMatchObject({ status: 'SUPPRESSED', candidateDeduction: 5, effectiveDeduction: 0, suppressedAmount: 5 });
    expect(effectiveByTarget['TEAM_HEALTH']).toBe(12); // not 17: the suppressed 5 never reaches the score
    expect(run.dimensions.TEAM_HEALTH).toMatchObject({ rawScore: 25, effectivePenalty: 12, adjustedScore: 13 });
    expect(run.leadershipIntelligenceScore).toBe(67);
  });

  test('identical inputs and configuration give identical results', async ({ request }) => {
    // recalculating creates runs, so it happens in a sandbox organization and never touches the seeded series
    const leader = await asPersona(request, 'sandbox-leader');
    const created = await (
      await leader.post('/calculate', {
        assessment: { organizationId: 'org-sandbox', templateId: 'tpl-1', templateVersion: '1', status: 'COMPLETED', completedAt: '2026-11-01T09:00:00Z' },
        inputs: answersFor(2),
      })
    ).json();
    await checkDeterminism(leader, created);
  });

  test('missing answers are never read as zero: too little data gives no score at all', async ({ request }) => {
    const leader = await asPersona(request, 'sandbox-leader');
    const response = await leader.post('/calculate', {
      assessment: { organizationId: 'org-sandbox', templateId: 'tpl-insufficient', templateVersion: '1', status: 'COMPLETED', completedAt: '2026-11-01T09:00:00Z' },
      inputs: [{ questionId: 'Q01', answer: 2 }],
    });
    const run = await response.json();
    expect(run.scoreStatus).toBe('INSUFFICIENT_DATA');
    expect(run.leadershipIntelligenceScore).toBeUndefined();
    expect(run.overall?.finalScore).toBeUndefined();
    for (const d of Object.values<any>(run.dimensions ?? {}).filter((d) => d.status !== 'VALID')) {
      expect(d.adjustedScore, 'an insufficient dimension has no score, not zero').toBeUndefined();
    }
  });
});
