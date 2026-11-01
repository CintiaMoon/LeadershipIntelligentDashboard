import type { Imbalance, Pattern, Penalty } from '@/types/views';
import { CONFIDENCE_BANDS, DIMENSIONS, SEVERITIES, severityRank } from '@/utils/labels';

// Pure selection and grouping of what the backend returned. Nothing here calculates a score, severity or confidence.

export const detectedPatterns = (patterns: Pattern[]) => patterns.filter((p) => p.status === 'MATCHED');

/** Condition met, but the evidence or confidence was not enough to treat it as a finding. */
export const insufficientEvidencePatterns = (patterns: Pattern[]) => patterns.filter((p) => p.status === 'INELIGIBLE');

export function countBySeverity(patterns: Pattern[]): Record<string, number> {
  const counts: Record<string, number> = Object.fromEntries(SEVERITIES.map((s) => [s, 0]));
  for (const p of detectedPatterns(patterns)) counts[p.severity] = (counts[p.severity] ?? 0) + 1;
  return counts;
}

export function sortPatterns(patterns: Pattern[]): Pattern[] {
  return [...patterns].sort((a, b) => severityRank(a.severity) - severityRank(b.severity) || a.patternId.localeCompare(b.patternId));
}

/** The dimension a pattern belongs to, plus every dimension a penalty it triggered is aimed at. */
export function affectedDimensions(pattern: Pattern, penalties: Penalty[]): string[] {
  const dims = new Set<string>();
  if ((DIMENSIONS as readonly string[]).includes(pattern.category)) dims.add(pattern.category);
  for (const penalty of penalties) {
    if (penalty.triggerPatternId === pattern.patternId && penalty.target !== 'OVERALL') dims.add(penalty.target);
  }
  return DIMENSIONS.filter((d) => dims.has(d));
}

export type GroupBy = 'dimension' | 'severity' | 'confidence';

export interface Group {
  key: string;
  patterns: Pattern[];
}

export function groupPatterns(patterns: Pattern[], by: GroupBy): Group[] {
  const order: readonly string[] =
    by === 'dimension' ? [...DIMENSIONS, 'CROSS_DIMENSIONAL'] : by === 'severity' ? SEVERITIES : [...CONFIDENCE_BANDS];
  const keyOf = (p: Pattern) => (by === 'dimension' ? p.category : by === 'severity' ? p.severity : (p.confidenceBand ?? 'INSUFFICIENT'));
  const buckets = new Map<string, Pattern[]>();
  for (const p of sortPatterns(patterns)) {
    const key = keyOf(p);
    buckets.set(key, [...(buckets.get(key) ?? []), p]);
  }
  const known = order.filter((k) => buckets.has(k));
  const unknown = [...buckets.keys()].filter((k) => !order.includes(k)).sort();
  return [...known, ...unknown].map((key) => ({ key, patterns: buckets.get(key) ?? [] }));
}

export interface DimensionRisk {
  highestSeverity: string | null;
  detected: number;
  appliedPenalties: number;
  suppressedPenalties: number;
}

export function dimensionRisk(dimension: string, patterns: Pattern[], penalties: Penalty[]): DimensionRisk {
  const own = detectedPatterns(patterns).filter((p) => p.category === dimension);
  const targeted = penalties.filter((p) => p.target === dimension);
  const severities = own.map((p) => p.severity).sort((a, b) => severityRank(a) - severityRank(b));
  return {
    highestSeverity: severities[0] ?? null,
    detected: own.length,
    appliedPenalties: targeted.filter((p) => p.status === 'APPLIED').length,
    suppressedPenalties: targeted.filter((p) => p.status === 'SUPPRESSED').length,
  };
}

export function groupPenalties(penalties: Penalty[]): Record<'APPLIED' | 'SUPPRESSED' | 'INELIGIBLE' | 'NOT_TRIGGERED', Penalty[]> {
  const result = { APPLIED: [] as Penalty[], SUPPRESSED: [] as Penalty[], INELIGIBLE: [] as Penalty[], NOT_TRIGGERED: [] as Penalty[] };
  for (const p of penalties) {
    if (p.status in result) result[p.status as keyof typeof result].push(p);
  }
  return result;
}

export const detectedImbalances = (imbalances: Imbalance[]) => imbalances.filter((i) => i.status === 'DETECTED');
