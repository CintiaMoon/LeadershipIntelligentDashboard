import { describe, expect, it } from 'vitest';
import { formatChange, formatDate, formatScore, formatScoreCompact, humanizeCode, percentOf } from './presentation';

describe('presentation helpers', () => {
  it('keeps the two-decimal scale of the backend and never turns a missing value into zero', () => {
    expect(formatScore(67)).toBe('67.00');
    expect(formatScore('67.5')).toBe('67.50');
    expect(formatScore(0)).toBe('0.00');
    for (const missing of [null, undefined, '', 'abc', Number.NaN]) {
      expect(formatScore(missing as never)).toBe('\u2013');
    }
    expect(formatScoreCompact(67.25)).toBe('67.3');
  });

  it('shows changes with an explicit sign', () => {
    expect(formatChange(4.5)).toBe('+4.50');
    expect(formatChange(-2)).toBe('-2.00');
    expect(formatChange(0)).toBe('0.00');
    expect(formatChange(null)).toBe('\u2013');
  });

  it('positions values on a band for charts only', () => {
    expect(percentOf(50, 0, 100)).toBe(50);
    expect(percentOf(150, 0, 100)).toBe(100);
    expect(percentOf(-5, 0, 100)).toBe(0);
    expect(percentOf(null, 0, 100)).toBe(0);
    expect(percentOf(10, 5, 5)).toBe(0);
  });

  it('formats dates and codes defensively', () => {
    expect(formatDate('2026-11-01T09:00:00Z')).toMatch(/2026/);
    expect(formatDate('not a date')).toBe('\u2013');
    expect(formatDate(undefined)).toBe('\u2013');
    expect(humanizeCode('TEAM_HEALTH')).toBe('Team health');
    expect(humanizeCode(null)).toBe('\u2013');
  });
});
