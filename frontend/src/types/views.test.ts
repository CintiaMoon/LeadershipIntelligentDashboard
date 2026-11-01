import { describe, expect, it } from 'vitest';
import { patternsResponse } from '@/tests/fixtures';
import { UnexpectedResponseError, parseDimensionDetails, parseEffectiveByTarget, parsePatterns, parsePenalties } from './views';

describe('response validation', () => {
  it('accepts the backend shape and keeps unknown extra fields', () => {
    const parsed = parsePatterns([{ ...patternsResponse.patterns[0], addedLater: 'x' }]);
    expect(parsed[0]).toMatchObject({ patternId: 'TH-001', addedLater: 'x' });
  });

  it('treats absent lists as empty, never as an error', () => {
    expect(parsePatterns(undefined)).toEqual([]);
    expect(parsePenalties(null)).toEqual([]);
    expect(parseDimensionDetails(undefined)).toEqual([]);
    expect(parseEffectiveByTarget(undefined)).toEqual({});
  });

  it('rejects an unexpected shape with a clear error and no stack of backend data', () => {
    expect(() => parsePatterns([{ patternId: 5 }])).toThrow(UnexpectedResponseError);
    expect(() => parsePenalties('nope')).toThrow('penalties response was not in the expected format');
    expect(() => parseEffectiveByTarget({ TEAM_HEALTH: 'twelve' })).toThrow(UnexpectedResponseError);
  });

  it('accepts boolean and text evidence flags', () => {
    const base = patternsResponse.patterns[0];
    const parsed = parsePatterns([{ ...base, evidence: [{ kind: 'SIGNAL', operandKey: 'signal:SINGLE_POINT_OF_FAILURE', flag: true, reliability: 0.8 }, { kind: 'SIGNAL', operandKey: 'signal:X', flag: 'YES' }] }]);
    expect(parsed[0]?.evidence.map((e) => e.flag)).toEqual([true, 'YES']);
  });

  it('fills defaults for redacted patterns without values', () => {
    const parsed = parsePatterns([{ patternId: 'P', code: 'C', version: 1, category: 'TEAM_HEALTH', severity: 'LOW', status: 'MATCHED', reason: 'CONDITION_SATISFIED' }]);
    expect(parsed[0]?.evidence).toEqual([]);
    expect(parsed[0]?.actionKeys).toEqual([]);
  });
});
