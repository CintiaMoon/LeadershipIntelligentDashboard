import { describe, expect, it } from 'vitest';
import { makeNames } from '@/features/dashboard/display';
import { catalog } from '@/tests/fixtures';
import { describeLeaf, toConditionView } from './conditions';

const names = makeNames(catalog);

describe('condition wording', () => {
  it('rewrites a backend leaf with display names, a readable operator and the observed value', () => {
    expect(describeLeaf({ operator: 'GTE', outcome: 'TRUE', detail: 'signal:WORKLOAD_PRESSURE=80.0000000000 GTE 75' }, names)).toBe(
      'Workload pressure is at least 75 (it was 80.00)',
    );
    expect(describeLeaf({ operator: 'LTE', outcome: 'TRUE', detail: 'signal:TEAM_SUSTAINABILITY=25.0000000000 LTE 35' }, names)).toBe('Team sustainability is at most 35 (it was 25.00)');
  });

  it('shows the backend text unchanged when it has an unfamiliar shape', () => {
    expect(describeLeaf({ operator: 'EXISTS', outcome: 'TRUE', detail: 'signal:X present' }, names)).toBe('signal:X present');
    expect(describeLeaf({ operator: 'MYSTERY', outcome: 'TRUE', detail: 'a=1 MYSTERY 2' }, names)).toBe('a=1 MYSTERY 2');
  });

  it('describes groups and maps all three outcomes without reading unknown as false', () => {
    const view = toConditionView(
      {
        operator: 'AND',
        outcome: 'UNKNOWN',
        children: [
          { operator: 'GTE', outcome: 'TRUE', detail: 'signal:WORKLOAD_PRESSURE=80 GTE 75' },
          { operator: 'LTE', outcome: 'FALSE', detail: 'signal:TEAM_SUSTAINABILITY=50 LTE 35' },
          { operator: 'GT', outcome: 'UNKNOWN', detail: 'signal:MISSING missing' },
        ],
      },
      names,
    );
    expect(view.text).toBe('All of these must be met');
    expect(view.outcome).toBe('unknown');
    expect(view.children.map((c) => c.outcome)).toEqual(['met', 'not-met', 'unknown']);
  });

  it('describes OR and NOT groups', () => {
    expect(toConditionView({ operator: 'OR', outcome: 'TRUE', children: [{ operator: 'EQ', outcome: 'TRUE', detail: '' }] }, names).text).toBe('At least one of these must be met');
    expect(toConditionView({ operator: 'NOT', outcome: 'TRUE', children: [{ operator: 'EQ', outcome: 'FALSE', detail: '' }] }, names).text).toBe('None of these may be met');
  });
});
