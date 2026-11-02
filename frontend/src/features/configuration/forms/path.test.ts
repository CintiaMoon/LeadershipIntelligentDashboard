import { describe, expect, it } from 'vitest';
import { getIn, setIn } from './path';

describe('path helpers', () => {
  const doc = { overall: { weights: { A: 0.5 } }, list: [{ id: 'x', n: 1 }, { id: 'y', n: 2 }] };

  it('reads nested values and says undefined for a missing step', () => {
    expect(getIn(doc, ['overall', 'weights', 'A'])).toBe(0.5);
    expect(getIn(doc, ['list', 1, 'id'])).toBe('y');
    expect(getIn(doc, ['overall', 'nothing', 'A'])).toBeUndefined();
    expect(getIn(undefined, ['a'])).toBeUndefined();
  });

  it('replaces a value without changing the original and shares what it did not touch', () => {
    const next = setIn(doc, ['list', 0, 'n'], 9);
    expect(getIn(next, ['list', 0, 'n'])).toBe(9);
    expect(doc.list[0]!.n).toBe(1);
    expect(next.list[1]).toBe(doc.list[1]);
    expect(next.overall).toBe(doc.overall);
  });

  it('keeps the order of keys and builds missing objects and arrays', () => {
    expect(Object.keys(setIn(doc.overall.weights, ['A'], 1))).toEqual(['A']);
    expect(setIn({}, ['a', 'b'], 1)).toEqual({ a: { b: 1 } });
    expect(setIn({}, ['a', 0], 1)).toEqual({ a: [1] });
  });
});
