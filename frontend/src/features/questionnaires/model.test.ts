import { describe, expect, it } from 'vitest';
import type { QuestionSpec } from '@/types/api';
import { KINDS, changeKind, moveItem, newOptionKey, newQuestion, newRule, nextQuestionId, sameDocument, summarize } from './model';

const q = (id: string, extra: Partial<QuestionSpec> = {}): QuestionSpec => ({ id, kind: 'RATING', text: id, required: false, options: [], labels: [], ...extra });

describe('nextQuestionId', () => {
  it('starts at Q01 and continues after the highest id', () => {
    expect(nextQuestionId([])).toBe('Q01');
    expect(nextQuestionId([q('Q01'), q('Q02')])).toBe('Q03');
    expect(nextQuestionId([q('Q05'), q('Q02')])).toBe('Q06');
  });

  it('never reuses an id that is taken, whatever it looks like', () => {
    expect(nextQuestionId([q('Q01'), q('Custom')])).toBe('Q02');
    expect(nextQuestionId([q('Q99')])).toBe('Q100');
  });
});

describe('newQuestion', () => {
  it.each(KINDS.map((k) => [k.kind]))('gives a %s question the settings that kind needs', (kind) => {
    const created = newQuestion(kind, [q('Q01')]);
    expect(created.id).toBe('Q02');
    expect(created.kind).toBe(kind);
    expect(created.required).toBe(false);
    if (kind === 'SINGLE_CHOICE' || kind === 'MULTI_CHOICE') expect(created.options).toHaveLength(2);
    if (kind === 'LIKERT') expect(created.points).toBe(5);
    if (kind === 'NUMERIC') expect([created.min, created.max]).toEqual([0, 10]);
    if (kind === 'YES_NO') expect(created.desirableAnswer).toBe('YES');
    if (kind === 'FREE_TEXT') expect(created.dimension).toBeUndefined();
  });
});

describe('changeKind', () => {
  it('keeps what still applies and drops what does not', () => {
    const choice = q('Q01', { kind: 'SINGLE_CHOICE', text: 'Which?', help: 'pick', required: true, dimension: 'TEAM_HEALTH', weight: 2, options: [{ key: 'A', label: 'a' }, { key: 'B', label: 'b' }] });
    const rating = changeKind(choice, 'RATING');
    expect(rating).toMatchObject({ id: 'Q01', kind: 'RATING', text: 'Which?', help: 'pick', required: true, dimension: 'TEAM_HEALTH', weight: 2, higherIsBetter: true });
    expect(rating.options).toEqual([]);
  });

  it('keeps the options when switching between single and multiple choice', () => {
    const choice = q('Q01', { kind: 'SINGLE_CHOICE', options: [{ key: 'A', label: 'a' }, { key: 'B', label: 'b' }, { key: 'C', label: 'c' }] });
    expect(changeKind(choice, 'MULTI_CHOICE').options).toHaveLength(3);
  });

  it('removes the dimension and the weight when the question becomes free text, which is never scored', () => {
    const text = changeKind(q('Q01', { dimension: 'TECHNICAL_RISK', weight: 3 }), 'FREE_TEXT');
    expect(text.dimension).toBeUndefined();
    expect(text.weight).toBeUndefined();
  });

  it('gives a question that becomes a choice two starting options', () => {
    expect(changeKind(q('Q01'), 'SINGLE_CHOICE').options).toHaveLength(2);
  });
});

describe('small helpers', () => {
  it('moves an item, and ignores a move off either end', () => {
    expect(moveItem(['a', 'b', 'c'], 0, 1)).toEqual(['b', 'a', 'c']);
    expect(moveItem(['a', 'b', 'c'], 2, 1)).toEqual(['a', 'c', 'b']);
    const same = ['a', 'b'];
    expect(moveItem(same, 0, -1)).toBe(same);
    expect(moveItem(same, 1, 2)).toBe(same);
  });

  it('picks an option key that is not taken', () => {
    expect(newOptionKey([{ key: 'O1' }, { key: 'O2' }])).toBe('O3');
    expect(newOptionKey([{ key: 'O2' }])).toBe('O3');
    expect(newOptionKey([{ key: 'O1' }, { key: 'O3' }])).toBe('O4');
  });

  it('starts a rule on the first question that can be compared', () => {
    const rule = newRule([q('Q01', { kind: 'FREE_TEXT' }), q('Q02')]);
    expect(rule.condition).toMatchObject({ op: 'LTE', question: 'Q02', value: 25 });
    expect(rule.enabled).toBe(true);
    expect(rule.severity).toBe('MEDIUM');
  });

  it('compares documents by content', () => {
    expect(sameDocument({ name: 'a', questions: [q('Q01')] }, { name: 'a', questions: [q('Q01')] })).toBe(true);
    expect(sameDocument({ name: 'a' }, { name: 'b' })).toBe(false);
  });
});

describe('summarize', () => {
  it('counts questions, required ones, scored ones, rules and the dimensions that have a scored question', () => {
    const summary = summarize({
      questions: [q('Q01', { required: true, dimension: 'TEAM_HEALTH' }), q('Q02', { dimension: 'TEAM_HEALTH' }), q('Q03', { kind: 'FREE_TEXT', dimension: 'TECHNICAL_RISK' }), q('Q04')],
      rules: [{ name: 'r' }],
    });
    expect(summary).toEqual({ questions: 4, required: 1, dimensions: 1, scored: 2, rules: 1 });
  });

  it('copes with an empty document', () => {
    expect(summarize({})).toEqual({ questions: 0, required: 0, dimensions: 0, scored: 0, rules: 0 });
  });
});
