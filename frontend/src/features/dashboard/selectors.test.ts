import { describe, expect, it } from 'vitest';
import { parsePatterns, parsePenalties } from '@/types/views';
import { patternsResponse, penaltiesResponse } from '@/tests/fixtures';
import { affectedDimensions, countBySeverity, detectedPatterns, dimensionRisk, groupPatterns, groupPenalties, insufficientEvidencePatterns } from './selectors';

const patterns = parsePatterns(patternsResponse.patterns);
const penalties = parsePenalties(penaltiesResponse.penalties);

describe('selectors', () => {
  it('separates detected patterns from those without enough evidence and from non-matches', () => {
    expect(detectedPatterns(patterns).map((p) => p.patternId)).toEqual(['TH-001', 'DS-001', 'XD-001']);
    expect(insufficientEvidencePatterns(patterns).map((p) => p.patternId)).toEqual(['TR-001']);
  });

  it('counts detected patterns per severity, counting only detected ones', () => {
    expect(countBySeverity(patterns)).toEqual({ CRITICAL: 1, HIGH: 1, MEDIUM: 1, LOW: 0 });
  });

  it('groups by dimension in the fixed dimension order, then cross-dimensional', () => {
    const groups = groupPatterns(detectedPatterns(patterns), 'dimension');
    expect(groups.map((g) => g.key)).toEqual(['TEAM_HEALTH', 'DELIVERY_STABILITY', 'CROSS_DIMENSIONAL']);
  });

  it('groups by severity from most to least severe and by confidence from strongest', () => {
    expect(groupPatterns(detectedPatterns(patterns), 'severity').map((g) => g.key)).toEqual(['CRITICAL', 'HIGH', 'MEDIUM']);
    expect(groupPatterns(detectedPatterns(patterns), 'confidence').map((g) => g.key)).toEqual(['STRONG', 'MODERATE', 'WEAK']);
  });

  it('puts unknown group keys last in a stable order', () => {
    const odd = parsePatterns([{ ...patternsResponse.patterns[0], patternId: 'ZZ-1', category: 'SOMETHING_NEW' }, ...patternsResponse.patterns.slice(0, 1)]);
    expect(groupPatterns(odd, 'dimension').map((g) => g.key)).toEqual(['TEAM_HEALTH', 'SOMETHING_NEW']);
  });

  it('lists the dimension of a pattern and the targets of penalties it triggered', () => {
    const cross = patterns.find((p) => p.patternId === 'XD-001');
    const team = patterns.find((p) => p.patternId === 'TH-001');
    expect(affectedDimensions(team as never, penalties)).toEqual(['TEAM_HEALTH']);
    // a cross-dimensional pattern belongs to no single dimension and its penalty targets the overall score
    expect(affectedDimensions(cross as never, penalties)).toEqual([]);
  });

  it('summarizes risk per dimension from findings, not from scores', () => {
    expect(dimensionRisk('TEAM_HEALTH', patterns, penalties)).toEqual({ highestSeverity: 'CRITICAL', detected: 1, appliedPenalties: 1, suppressedPenalties: 0 });
    expect(dimensionRisk('DELIVERY_STABILITY', patterns, penalties)).toEqual({ highestSeverity: 'HIGH', detected: 1, appliedPenalties: 0, suppressedPenalties: 1 });
    expect(dimensionRisk('LEADERSHIP_LOAD', patterns, penalties)).toEqual({ highestSeverity: null, detected: 0, appliedPenalties: 0, suppressedPenalties: 0 });
  });

  it('groups penalties by their four terminal states only', () => {
    const groups = groupPenalties(penalties);
    expect(Object.keys(groups)).toEqual(['APPLIED', 'SUPPRESSED', 'INELIGIBLE', 'NOT_TRIGGERED']);
    expect(groups.SUPPRESSED).toHaveLength(1);
  });
});
