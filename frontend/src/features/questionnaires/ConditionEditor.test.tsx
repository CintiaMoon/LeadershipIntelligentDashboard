import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import type { QuestionSpec } from '@/types/api';
import { ConditionEditor } from './ConditionEditor';
import type { Cond } from './model';

const questions: QuestionSpec[] = [
  { id: 'Q01', kind: 'RATING', text: 'How is morale?', options: [], labels: [] },
  { id: 'Q02', kind: 'YES_NO', title: 'Incidents', text: 'Any incident?', options: [], labels: [] },
  { id: 'Q03', kind: 'FREE_TEXT', text: 'Comments', options: [], labels: [] },
];

/** Holds the condition in state, as a rule does, and shows it as JSON so a test can read what the editor produced. */
function Harness({ start, readOnly = false }: { start: Cond; readOnly?: boolean }) {
  const [value, setValue] = useState(start);
  return (
    <>
      <ConditionEditor value={value} onChange={setValue} questions={questions} idPrefix="c" readOnly={readOnly} />
      <pre data-testid="out">{JSON.stringify(value)}</pre>
    </>
  );
}
const out = (): Cond => JSON.parse(screen.getByTestId('out').textContent ?? '{}') as Cond;

describe('ConditionEditor: a comparison', () => {
  it('offers the questions that have a value and the four dimensions, never free text', () => {
    render(<Harness start={{ op: 'LTE', question: 'Q01', value: 25 }} />);
    const options = within(screen.getByLabelText('What is compared')).getAllByRole('option').map((o) => o.getAttribute('value'));
    expect(options).toEqual(['question:Q01', 'question:Q02', 'dimension:TEAM_HEALTH', 'dimension:DELIVERY_STABILITY', 'dimension:TECHNICAL_RISK', 'dimension:LEADERSHIP_LOAD']);
  });

  it('changes what is compared between a question and a dimension', async () => {
    render(<Harness start={{ op: 'LTE', question: 'Q01', value: 25 }} />);
    await userEvent.selectOptions(screen.getByLabelText('What is compared'), 'dimension:TECHNICAL_RISK');
    expect(out()).toMatchObject({ op: 'LTE', dimension: 'TECHNICAL_RISK', value: 25 });
    expect(out().question).toBeUndefined();
  });

  it('edits the value as a number and clears it to nothing, never to zero', async () => {
    render(<Harness start={{ op: 'LTE', question: 'Q01', value: 25 }} />);
    await userEvent.clear(screen.getByLabelText('Value'));
    expect(out().value).toBeUndefined();
    await userEvent.type(screen.getByLabelText('Value'), '40');
    expect(out().value).toBe(40);
  });

  it('shows the inputs each operator needs', async () => {
    render(<Harness start={{ op: 'LTE', question: 'Q01', value: 25 }} />);
    await userEvent.selectOptions(screen.getByLabelText('Condition type'), 'BETWEEN');
    expect(screen.getByLabelText('From')).toBeInTheDocument();
    expect(screen.getByLabelText('To')).toBeInTheDocument();
    expect(screen.queryByLabelText('Value')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('From'), '10');
    await userEvent.type(screen.getByLabelText('To'), '60');
    expect(out()).toMatchObject({ op: 'BETWEEN', low: 10, high: 60 });

    await userEvent.selectOptions(screen.getByLabelText('Condition type'), 'IN');
    await userEvent.type(screen.getByLabelText('Values, separated by commas'), '0, 25, x, 50');
    expect(out().values).toEqual([0, 25, 50]);

    await userEvent.selectOptions(screen.getByLabelText('Condition type'), 'EXISTS');
    expect(screen.queryByLabelText('Value')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('From')).not.toBeInTheDocument();
  });
});

describe('ConditionEditor: groups', () => {
  it('turns a comparison into a group that keeps it, and adds conditions and groups', async () => {
    render(<Harness start={{ op: 'LTE', question: 'Q01', value: 25 }} />);
    await userEvent.selectOptions(screen.getAllByLabelText('Condition type')[0]!, 'AND');
    expect(out()).toMatchObject({ op: 'AND', conditions: [{ op: 'LTE', question: 'Q01', value: 25 }] });
    await userEvent.click(screen.getByRole('button', { name: 'Add condition' }));
    expect(out().conditions).toHaveLength(2);
    await userEvent.click(screen.getByRole('button', { name: 'Add group' }));
    expect(out().conditions).toHaveLength(3);
    expect(out().conditions?.[2]).toMatchObject({ op: 'AND' });
  });

  it('removes a condition from a group, but never the last one', async () => {
    render(<Harness start={{ op: 'OR', conditions: [{ op: 'LT', question: 'Q01', value: 10 }, { op: 'GT', question: 'Q02', value: 90 }] }} />);
    expect(screen.getAllByRole('button', { name: 'Remove this condition' })).toHaveLength(2);
    await userEvent.click(screen.getAllByRole('button', { name: 'Remove this condition' })[0]!);
    expect(out().conditions).toEqual([{ op: 'GT', question: 'Q02', value: 90 }]);
    expect(screen.queryByRole('button', { name: 'Remove this condition' })).not.toBeInTheDocument();
  });

  it('switches between all of and any of without losing the conditions', async () => {
    render(<Harness start={{ op: 'AND', conditions: [{ op: 'LT', question: 'Q01', value: 10 }, { op: 'GT', question: 'Q02', value: 90 }] }} />);
    await userEvent.selectOptions(screen.getAllByLabelText('Condition type')[0]!, 'OR');
    expect(out()).toMatchObject({ op: 'OR' });
    expect(out().conditions).toHaveLength(2);
  });

  it('wraps one condition in none of, and leaves it with exactly one', async () => {
    render(<Harness start={{ op: 'AND', conditions: [{ op: 'LT', question: 'Q01', value: 10 }, { op: 'GT', question: 'Q02', value: 90 }] }} />);
    await userEvent.selectOptions(screen.getAllByLabelText('Condition type')[0]!, 'NOT');
    expect(out()).toEqual({ op: 'NOT', condition: { op: 'LT', question: 'Q01', value: 10 } });
    expect(screen.queryByRole('button', { name: 'Add condition' })).not.toBeInTheDocument();
  });

  it('turns a group back into a comparison', async () => {
    render(<Harness start={{ op: 'AND', conditions: [{ op: 'LT', question: 'Q01', value: 10 }] }} />);
    await userEvent.selectOptions(screen.getAllByLabelText('Condition type')[0]!, 'GTE');
    expect(out()).toMatchObject({ op: 'GTE', question: 'Q01' });
    expect(out().conditions).toBeUndefined();
  });

  it('stops adding groups at a sensible depth', async () => {
    const deep: Cond = { op: 'AND', conditions: [{ op: 'AND', conditions: [{ op: 'AND', conditions: [{ op: 'AND', conditions: [{ op: 'LT', question: 'Q01', value: 1 }] }] }] }] };
    render(<Harness start={deep} />);
    // the innermost group (depth 3) cannot hold another group
    expect(screen.getAllByRole('button', { name: 'Add group' })).toHaveLength(3);
    expect(screen.getAllByRole('button', { name: 'Add condition' })).toHaveLength(4);
  });
});

describe('ConditionEditor: read only', () => {
  it('shows the condition without any control that changes it', () => {
    render(<Harness readOnly start={{ op: 'AND', conditions: [{ op: 'LT', question: 'Q01', value: 10 }] }} />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Value')).toHaveAttribute('readonly');
    expect(screen.getAllByLabelText('Condition type')[0]).toBeDisabled();
  });
});
