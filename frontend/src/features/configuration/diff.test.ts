import { describe, expect, it } from 'vitest';
import { deepEqual, diffDocuments, formatValue, jsonProblem, summarizeChanges } from './diff';

const base = {
  questions: [
    { id: 'Q01', type: 'SCALE', normalization: { min: 1, max: 5 } },
    { id: 'Q02', type: 'BOOLEAN', normalization: { trueValue: 0, falseValue: 100 } },
  ],
  patterns: [{ id: 'TH-001', severity: 'CRITICAL', evidence: { minimumSignals: 2, minimumConfidence: 0.7 }, condition: { operator: 'LTE', signal: 'S1', value: 25 } }],
  dimensions: { settings: { TEAM_HEALTH: { requiredCoverage: 0.75 } }, mappings: [{ question: 'Q01', dimension: 'TEAM_HEALTH', weight: 1 }] },
  overall: { weights: { TEAM_HEALTH: 0.25, DELIVERY_STABILITY: 0.25 }, tolerance: 0.0001, lisInterpretation: [{ min: 0, max: 40, label: 'CRITICAL' }, { min: 40, max: 100, label: 'OK' }] },
};
const clone = () => structuredClone(base) as typeof base;

describe('diffDocuments', () => {
  it('finds nothing between equal documents', () => {
    expect(diffDocuments(base, clone())).toEqual([]);
  });

  it('shows an added and a removed list item by its identifier', () => {
    const next = clone();
    next.questions.push({ id: 'Q03', type: 'SCALE', normalization: { min: 1, max: 5 } });
    next.questions.shift();
    expect(diffDocuments(base, next)).toEqual([
      { section: 'questions', item: 'Q01', kind: 'removed', fields: [] },
      { section: 'questions', item: 'Q03', kind: 'added', fields: [] },
    ]);
  });

  it('shows one changed threshold of one pattern as that and nothing else', () => {
    const next = clone();
    next.patterns[0]!.evidence.minimumConfidence = 0.5;
    next.patterns[0]!.condition.value = 30;
    expect(diffDocuments(base, next)).toEqual([
      {
        section: 'patterns',
        item: 'TH-001',
        kind: 'changed',
        fields: [
          { path: 'condition.value', before: 25, after: 30 },
          { path: 'evidence.minimumConfidence', before: 0.7, after: 0.5 },
        ],
      },
    ]);
  });

  it('does not mind the order of a list that has identifiers', () => {
    const next = clone();
    next.questions.reverse();
    expect(diffDocuments(base, next)).toEqual([]);
  });

  it('compares an object section field by field with the path', () => {
    const next = clone();
    next.overall.weights.TEAM_HEALTH = 0.4;
    next.overall.tolerance = 0.001;
    expect(diffDocuments(base, next)).toEqual([
      {
        section: 'overall',
        item: null,
        kind: 'changed',
        fields: [
          { path: 'tolerance', before: 0.0001, after: 0.001 },
          { path: 'weights.TEAM_HEALTH', before: 0.25, after: 0.4 },
        ],
      },
    ]);
  });

  it('matches mappings by question and dimension and bands by label inside a section', () => {
    const next = clone();
    next.dimensions.mappings[0]!.weight = 2;
    next.overall.lisInterpretation[1]!.min = 50;
    const changes = diffDocuments(base, next);
    expect(changes.find((c) => c.section === 'dimensions')?.fields).toEqual([{ path: 'mappings[Q01 / TEAM_HEALTH].weight', before: 1, after: 2 }]);
    expect(changes.find((c) => c.section === 'overall')?.fields).toEqual([{ path: 'lisInterpretation[OK].min', before: 40, after: 50 }]);
  });

  it('shows a whole section that appears or disappears', () => {
    const next = clone() as Record<string, unknown>;
    next['responses'] = { minResponses: 5 };
    delete next['patterns'];
    const changes = diffDocuments(base, next);
    expect(changes.find((c) => c.section === 'responses')).toMatchObject({ kind: 'added', item: null });
    expect(changes.find((c) => c.section === 'patterns')).toMatchObject({ kind: 'removed', item: 'TH-001' });
  });

  it('compares a list without identifiers as a whole', () => {
    expect(diffDocuments({ x: { tags: ['a', 'b'] } }, { x: { tags: ['a', 'c'] } })).toEqual([
      { section: 'x', item: null, kind: 'changed', fields: [{ path: 'tags', before: ['a', 'b'], after: ['a', 'c'] }] },
    ]);
  });

  it('copes with a document that is not an object', () => {
    expect(diffDocuments(null, { questions: [{ id: 'Q01' }] })).toEqual([{ section: 'questions', item: 'Q01', kind: 'added', fields: [] }]);
    expect(diffDocuments(null, { overall: { tolerance: 1 } })).toEqual([{ section: 'overall', item: null, kind: 'added', fields: [{ path: '', before: undefined, after: { tolerance: 1 } }] }]);
    expect(diffDocuments(undefined, undefined)).toEqual([]);
  });

  it('counts what changed', () => {
    const next = clone();
    next.questions.push({ id: 'Q03', type: 'SCALE', normalization: { min: 1, max: 5 } });
    next.patterns[0]!.severity = 'LOW';
    next.overall.tolerance = 0.5;
    expect(summarizeChanges(diffDocuments(base, next))).toEqual({ sections: 3, added: 1, removed: 0, changed: 2 });
  });
});

describe('small helpers', () => {
  it('compares deeply', () => {
    expect(deepEqual({ a: [1, { b: 2 }] }, { a: [1, { b: 2 }] })).toBe(true);
    expect(deepEqual({ a: 1 }, { a: 1, b: undefined })).toBe(true);
    expect(deepEqual({ a: 1 }, { a: 2 })).toBe(false);
    expect(deepEqual([1, 2], [2, 1])).toBe(false);
  });

  it('formats a value for one line', () => {
    expect(formatValue(undefined)).toBe('(not set)');
    expect(formatValue('x')).toBe('"x"');
    expect(formatValue({ a: 1 })).toBe('{"a":1}');
    expect(formatValue('y'.repeat(200), 20)).toHaveLength(20);
  });

  it('says where the JSON fails, and that a configuration is an object', () => {
    expect(jsonProblem('{"a": 1}')).toBeNull();
    expect(jsonProblem('{"a": ')).toMatch(/JSON|Unexpected|position|Expected/i);
    expect(jsonProblem('[1, 2]')).toContain('JSON object');
    expect(jsonProblem('')).not.toBeNull();
  });
});
