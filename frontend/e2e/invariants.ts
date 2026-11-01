import { expect } from '@playwright/test';
import type { Persona } from './api';

// The mathematical invariants of the scoring contract (specv3.md 39.12), checked on real responses of the running system.
// Arithmetic is allowed here: these are tests that verify the backend, not application code that would compute scores.

const TOLERANCE = 0.011;
const within = (value: number, min: number, max: number) => value >= min - 1e-9 && value <= max + 1e-9;
const close = (a: number, b: number) => Math.abs(a - b) <= TOLERANCE;

export async function checkRunInvariants(admin: Persona, runId: string) {
  const run = await admin.json(`/scoring-runs/${runId}`);
  const patterns = (await admin.json(`/scoring-runs/${runId}/patterns`)).patterns as any[];
  const penaltyBody = await admin.json(`/scoring-runs/${runId}/penalties`);
  const penalties = penaltyBody.penalties as any[];
  const effectiveByTarget = penaltyBody.effectiveByTarget as Record<string, number>;
  const trace = (await admin.json(`/scoring-runs/${runId}/explanation`)).trace;
  const stage = (name: string) => trace.steps.find((s: any) => s.stage === name);

  // normalized values stay between 0 and 100; a missing answer has no value at all (never zero)
  for (const input of stage('NORMALIZATION').inputs as any[]) {
    if (input.normalizedValue === null || input.normalizedValue === undefined) {
      expect(['SIGNALS_ONLY', 'MISSING', 'IGNORED', 'SKIPPED'].some((s) => input.status.includes(s) || input.status === s), `${input.questionId} without a value has a status that says why`).toBeTruthy();
    } else {
      expect(within(input.normalizedValue, 0, 100), `normalized ${input.questionId}`).toBe(true);
    }
  }

  expect(run.scoreStatus).toBe('VALID');
  const lis = run.leadershipIntelligenceScore as number;
  expect(within(lis, 0, 100), 'LIS in range').toBe(true);

  let contributionSum = 0;
  for (const [name, d] of Object.entries<any>(run.dimensions)) {
    expect(within(d.rawScore, 0, 100), `${name} raw`).toBe(true);
    expect(within(d.adjustedScore, 0, 100), `${name} adjusted`).toBe(true);
    expect(d.effectivePenalty, `${name} penalty never negative`).toBeGreaterThanOrEqual(0);
    // penalties cannot increase a score, and adjusted = raw - penalty, never below zero
    expect(d.adjustedScore, `${name} adjusted <= raw`).toBeLessThanOrEqual(d.rawScore + 1e-9);
    expect(close(d.adjustedScore, Math.max(0, d.rawScore - d.effectivePenalty)), `${name} adjusted = raw - penalty`).toBe(true);
    contributionSum += d.weightedContribution;
  }

  // the weighted score is the sum of the weighted contributions; the overall penalty is applied once and never raises it
  const weighted = run.overall.weightedAdjustedScore as number;
  expect(close(contributionSum, weighted), 'weighted adjusted score = sum of contributions').toBe(true);
  expect(run.overall.effectiveOverallPenalty).toBeGreaterThanOrEqual(0);
  expect(lis).toBeLessThanOrEqual(weighted + 1e-9);
  expect(close(lis, Math.max(0, weighted - run.overall.effectiveOverallPenalty)), 'final = weighted - overall penalty').toBe(true);
  expect(close(run.overall.finalScore, lis)).toBe(true);

  // every penalty has one of four states; candidate = effective + suppressed; only applied penalties move scores
  const appliedByTarget: Record<string, number> = {};
  for (const p of penalties) {
    expect(['APPLIED', 'SUPPRESSED', 'INELIGIBLE', 'NOT_TRIGGERED'], `${p.penaltyId} state`).toContain(p.status);
    for (const v of [p.candidateDeduction, p.effectiveDeduction, p.suppressedAmount]) expect(v).toBeGreaterThanOrEqual(0);
    expect(close(p.candidateDeduction, p.effectiveDeduction + p.suppressedAmount), `${p.penaltyId} candidate = effective + suppressed`).toBe(true);
    expect(p.effectiveDeduction, `${p.penaltyId} effective <= candidate`).toBeLessThanOrEqual(p.candidateDeduction + 1e-9);
    if (p.status !== 'APPLIED') expect(p.effectiveDeduction, `${p.penaltyId} (${p.status}) changes nothing`).toBe(0);
    if (p.status === 'SUPPRESSED') expect(p.suppressedAmount, `${p.penaltyId} suppression is explained by an amount`).toBeGreaterThan(0);
    appliedByTarget[p.target] = (appliedByTarget[p.target] ?? 0) + p.effectiveDeduction;
  }
  for (const [target, total] of Object.entries(effectiveByTarget)) {
    // suppressed penalties never reach a score: the aggregated deduction is the applied ones, never more than requested
    expect(close(total, appliedByTarget[target] ?? 0), `effective ${target} = sum of applied`).toBe(true);
    if (target !== 'OVERALL') expect(close(run.dimensions[target].effectivePenalty, total), `${target} penalty on the result = aggregated`).toBe(true);
  }
  expect(close(run.overall.effectiveOverallPenalty, effectiveByTarget['OVERALL'] ?? 0), 'overall penalty applied once, as aggregated').toBe(true);

  // pattern detection alone changes no score: a dimension no applied penalty targets keeps its raw score
  for (const [name, d] of Object.entries<any>(run.dimensions)) {
    if ((appliedByTarget[name] ?? 0) === 0) expect(close(d.adjustedScore, d.rawScore), `${name} untouched without an applied penalty`).toBe(true);
  }
  // a pattern only counts as a finding when it was matched; confidence and severity are separate facts
  for (const p of patterns) {
    expect(typeof p.severity).toBe('string');
    if (p.status === 'MATCHED') expect(p.confidenceBand).toBeTruthy();
  }
  return { run, patterns, penalties, effectiveByTarget, trace };
}

/** Re-scoring the same assessment must reuse the run; recalculating must reproduce every score. */
export async function checkDeterminism(leader: Persona, run: any) {
  const again = await (await leader.post(`/assessments/${run.assessmentId}/score`)).json();
  expect(again.scoringRunId, 'the same inputs and configuration reuse the same run').toBe(run.scoringRunId);

  const recalculated = await (await leader.post(`/assessments/${run.assessmentId}/score/recalculate`)).json();
  expect(recalculated.scoringRunId, 'a recalculation is a new run').not.toBe(run.scoringRunId);
  expect(recalculated.configurationHash).toBe(run.configurationHash);
  expect(recalculated.inputHash).toBe(run.inputHash);
  expect(recalculated.leadershipIntelligenceScore).toBe(run.leadershipIntelligenceScore);
  expect(recalculated.dimensions).toEqual(run.dimensions);
  expect(recalculated.overall).toEqual(run.overall);
  expect(recalculated.patterns).toEqual(run.patterns);
  expect(recalculated.penalties).toEqual(run.penalties);
}
