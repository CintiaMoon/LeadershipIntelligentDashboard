import { describe, expect, it } from 'vitest';
import { redactedTraceResponse, traceResponse } from '@/tests/fixtures';
import { UnexpectedResponseError } from './views';
import { parseTrace } from './trace';

describe('parseTrace', () => {
  it('reads every stage of a full trace', () => {
    const trace = parseTrace(traceResponse.trace);
    expect(trace.header).toMatchObject({ engineVersion: '3.0.0', configurationVersion: 'platform_default:v1' });
    expect(trace.normalization).toHaveLength(3);
    expect(trace.signals).toHaveLength(2);
    expect(trace.rawDimensions).toHaveLength(4);
    expect(trace.patterns).toHaveLength(5);
    expect(trace.penalties).toHaveLength(4);
    expect(trace.effectiveByTarget).toMatchObject({ TEAM_HEALTH: 12 });
    expect(trace.adjusted).toHaveLength(4);
    expect(trace.dimensionLabels['TEAM_HEALTH']?.label).toBe('CRITICAL');
    expect(trace.overall).toMatchObject({ status: 'VALID', finalScore: 67, overallPenaltyApplied: 0 });
    expect(trace.interpretation?.label).toBe('HEALTHY');
  });

  it('treats the stages a viewer is not given as absent, not as empty', () => {
    const trace = parseTrace(redactedTraceResponse.trace);
    expect(trace.normalization).toBeUndefined();
    expect(trace.signals).toBeUndefined();
    expect(trace.rawDimensions[0]?.score.contributions).toEqual([]);
    expect(trace.overall?.finalScore).toBe(67);
  });

  it('keeps an insufficient-data trace without inventing scores', () => {
    const trace = parseTrace({ traceVersion: '1', header: traceResponse.trace.header, steps: [{ stage: 'OVERALL', overall: { status: 'INSUFFICIENT_DATA', dimensions: [] } }] });
    expect(trace.overall?.status).toBe('INSUFFICIENT_DATA');
    expect(trace.overall?.finalScore).toBeUndefined();
    expect(trace.adjusted).toEqual([]);
  });

  it('rejects a document that is not a trace', () => {
    expect(() => parseTrace({ nope: true })).toThrow(UnexpectedResponseError);
    expect(() => parseTrace({ traceVersion: '1', header: traceResponse.trace.header, steps: [{ stage: 'PATTERNS', patterns: [{ patternId: 1 }] }] })).toThrow(UnexpectedResponseError);
  });
});
