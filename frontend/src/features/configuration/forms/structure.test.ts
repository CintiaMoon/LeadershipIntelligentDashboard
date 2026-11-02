import { describe, expect, it } from 'vitest';
import { convert, defaultLeaf, describe as sentence, opKind, operandKind, type Targets } from './condition';
import { nextId, operandsOf, usesOfQuestion, usesOfSignal } from './refs';

const t: Targets = { signals: ['S1', 'S2'], questions: ['Q1', 'Q2'], dimensions: ['TEAM_HEALTH', 'TECHNICAL_RISK'] };

const doc = {
  questions: [{ id: 'Q1' }, { id: 'Q2' }],
  signals: [{ id: 'S1', sources: ['Q1'] }, { id: 'S2', sources: ['Q2'] }],
  dimensions: { mappings: [{ question: 'Q1', dimension: 'TEAM_HEALTH', weight: 1 }] },
  patterns: [
    { id: 'P1', condition: { operator: 'AND', conditions: [{ operator: 'LTE', signal: 'S1', value: 20 }, { operator: 'NOT', condition: { operator: 'GT', dimension: 'TEAM_HEALTH', value: 50 } }] } },
    { id: 'P2', condition: { operator: 'GAP_GTE', left: { signal: 'S2' }, right: { question: 'Q2' }, direction: 'LEFT_MINUS_RIGHT', value: 10 } },
  ],
};

describe('references', () => {
  it('lists every operand of a nested condition, including both sides of a gap', () => {
    expect(operandsOf(doc.patterns[0]!.condition)).toEqual(['signal:S1', 'dimension:TEAM_HEALTH']);
    expect(operandsOf(doc.patterns[1]!.condition)).toEqual(['signal:S2', 'question:Q2']);
  });

  it('says what still uses a question or a signal', () => {
    expect(usesOfQuestion(doc, 'Q1')).toEqual(['a dimension mapping (TEAM_HEALTH)', 'signal S1']);
    expect(usesOfQuestion(doc, 'Q2')).toEqual(['signal S2', 'pattern P2']);
    expect(usesOfSignal(doc, 'S1')).toEqual(['pattern P1']);
    expect(usesOfSignal(doc, 'S3')).toEqual([]);
  });

  it('finds the next free identifier', () => {
    expect(nextId('Q', ['Q01', 'Q02', 'Q04'])).toBe('Q03');
    expect(nextId('Q', [])).toBe('Q01');
  });
});

describe('condition conversion', () => {
  it('starts from a comparison of the first signal', () => {
    expect(defaultLeaf(t)).toEqual({ operator: 'LTE', signal: 'S1', value: 25 });
  });

  it('keeps the operand and the value between comparisons', () => {
    expect(convert({ operator: 'LTE', signal: 'S2', value: 30 }, 'GT', t)).toEqual({ operator: 'GT', signal: 'S2', value: 30 });
  });

  it('builds each shape the parser expects', () => {
    const leaf = { operator: 'LTE', dimension: 'TECHNICAL_RISK', value: 40 };
    expect(convert(leaf, 'BETWEEN', t)).toEqual({ operator: 'BETWEEN', dimension: 'TECHNICAL_RISK', low: 0, high: 100 });
    expect(convert(leaf, 'IN', t)).toEqual({ operator: 'IN', dimension: 'TECHNICAL_RISK', values: [40] });
    expect(convert(leaf, 'NOT_EXISTS', t)).toEqual({ operator: 'NOT_EXISTS', dimension: 'TECHNICAL_RISK' });
    expect(convert(leaf, 'CONTAINS', t)).toEqual({ operator: 'CONTAINS', question: 'Q1', signal: 'S1' });
    expect(convert(leaf, 'GAP_GT', t)).toEqual({ operator: 'GAP_GT', left: { dimension: 'TECHNICAL_RISK' }, right: { signal: 'S2' }, direction: 'LEFT_MINUS_RIGHT', value: 40 });
  });

  it('wraps a comparison in a group and unwraps it again without losing it', () => {
    const leaf = { operator: 'LTE', signal: 'S1', value: 20 };
    const and = convert(leaf, 'AND', t);
    expect(and).toEqual({ operator: 'AND', conditions: [leaf] });
    expect(convert(and, 'OR', t)).toEqual({ operator: 'OR', conditions: [leaf] });
    expect(convert(and, 'NOT', t)).toEqual({ operator: 'NOT', condition: leaf });
    expect(convert({ operator: 'AND', conditions: [] }, 'NOT', t)).toEqual({ operator: 'NOT', condition: defaultLeaf(t) });
  });

  it('turns a group into a comparison of the first signal', () => {
    expect(convert({ operator: 'AND', conditions: [] }, 'LT', t)).toEqual({ operator: 'LT', signal: 'S1', value: 25 });
  });

  it('classifies operators and operands', () => {
    expect(opKind('AND')).toBe('group');
    expect(opKind('GAP_GTE')).toBe('gap');
    expect(opKind('NOT_IN')).toBe('list');
    expect(operandKind({ dimension: 'X' })).toBe('dimension');
    expect(operandKind({ question: 'X' })).toBe('question');
    expect(operandKind({})).toBe('signal');
  });

  it('writes a condition as a sentence', () => {
    expect(sentence(doc.patterns[0]!.condition)).toBe('(S1 is at most 20 and not (TEAM_HEALTH is above 50))');
    expect(sentence(doc.patterns[1]!.condition)).toBe('S2 gap is at least 10 from Q2');
  });
});
