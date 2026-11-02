import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

// these forms are large and typed into character by character; under the load of a whole run the default five seconds is not enough
vi.setConfig({ testTimeout: 30_000 });
import { ConfigForms } from './ConfigForms';

// the real platform default, when the whole repository is present (the image build only has the frontend folder)
const found = import.meta.glob('../../../../../src/main/resources/config/initial-platform-default.json', { eager: true, import: 'default' });
const platformDefault = Object.values(found)[0] as { questions: unknown[]; signals: unknown[]; patterns: unknown[] } | undefined;

const base = () => ({
  questions: [
    { id: 'Q1', type: 'SCALE', missingPolicy: 'IGNORE', normalization: { min: 1, max: 5, direction: 'POSITIVE', outOfRangePolicy: 'REJECT', allowDecimals: false } },
    { id: 'Q2', type: 'MULTIPLE_CHOICE', missingPolicy: 'IGNORE', normalization: { values: { ALWAYS: 100, SOMETIMES: 50, NEVER: 0 }, unknownOption: 'REJECT' } },
    { id: 'Q3', type: 'BOOLEAN', missingPolicy: 'IGNORE', normalization: { trueValue: 0, falseValue: 100 } },
    { id: 'Q4', type: 'TEXT', missingPolicy: 'IGNORE', normalization: {} },
  ],
  signals: [
    { id: 'S1', dataType: 'NUMBER', sources: ['Q1'], derivationVersion: '1' },
    { id: 'S2', dataType: 'NUMBER', sources: ['Q2'], derivationVersion: '1', reliability: 0.8 },
    { id: 'UNUSED', dataType: 'NUMBER', sources: ['Q3'], derivationVersion: '1' },
  ],
  dimensions: { settings: {}, mappings: [{ question: 'Q1', dimension: 'TEAM_HEALTH', weight: 1 }, { question: 'Q2', dimension: 'TECHNICAL_RISK', weight: 2 }] },
  patterns: [
    {
      id: 'P1',
      code: 'BURNOUT',
      category: 'TEAM_HEALTH',
      severity: 'HIGH',
      enabled: true,
      penaltyEligible: true,
      evidence: { minimumSignals: 2, minimumConfidence: 0.7 },
      actions: ['REVIEW'],
      condition: { operator: 'AND', conditions: [{ operator: 'LTE', signal: 'S1', value: 25 }, { operator: 'GT', dimension: 'TECHNICAL_RISK', value: 50 }] },
    },
  ],
  imbalances: [{ id: 'GAP', left: 'DELIVERY_STABILITY', right: 'TECHNICAL_RISK', direction: 'LEFT_MINUS_RIGHT', operator: 'GAP_GTE', threshold: 30, severity: 'HIGH', enabled: true }],
  recommendations: [{ key: 'REVIEW', title: 't', description: 'd', priority: 1 }, { key: 'OTHER', title: 't', description: 'd', priority: 2 }],
});

function Harness({ initial, readOnly = false }: { initial: unknown; readOnly?: boolean }) {
  const [text, setText] = useState(JSON.stringify(initial, null, 2));
  return (
    <>
      <ConfigForms text={text} onChange={setText} readOnly={readOnly} />
      <pre data-testid="document">{text}</pre>
    </>
  );
}
const written = () => JSON.parse(screen.getByTestId('document').textContent ?? '');
const open = (name: string) => userEvent.click(screen.getByRole('button', { name }));

describe('question forms', () => {
  it('edits a scale, a choice list and a yes/no question and what each feeds', async () => {
    render(<Harness initial={base()} />);
    await open('Questions');
    const max = screen.getByLabelText('Highest answer (Q1)');
    await userEvent.clear(max);
    await userEvent.type(max, '7');
    await userEvent.selectOptions(screen.getByLabelText('Higher answer is (Q1)'), 'NEGATIVE');
    await userEvent.selectOptions(screen.getByLabelText('When not answered (Q1)'), 'NEUTRAL');
    const never = screen.getByLabelText('Never (Q2)');
    await userEvent.clear(never);
    await userEvent.type(never, '10');
    const yes = screen.getByLabelText('Score for yes (Q3)');
    await userEvent.clear(yes);
    await userEvent.type(yes, '20');
    const doc = written();
    expect(doc.questions[0].normalization).toMatchObject({ max: 7, direction: 'NEGATIVE', min: 1 });
    expect(doc.questions[0].missingPolicy).toBe('NEUTRAL');
    expect(doc.questions[1].normalization.values).toEqual({ ALWAYS: 100, SOMETIMES: 50, NEVER: 10 });
    expect(doc.questions[2].normalization.trueValue).toBe(20);
    expect(screen.getByText(/Text question is edited in the JSON view|normalization of a Text question/)).toBeInTheDocument();
  });

  it('changes the weight a question gives, adds a dimension and takes one away', async () => {
    render(<Harness initial={base()} />);
    await open('Questions');
    const weight = screen.getByLabelText('Weight (Q2, Technical risk)');
    await userEvent.clear(weight);
    await userEvent.type(weight, '3');
    expect(written().dimensions.mappings[1].weight).toBe(3);
    await userEvent.click(screen.getByRole('button', { name: 'Add a dimension to Q1' }));
    expect(written().dimensions.mappings).toHaveLength(3);
    expect(written().dimensions.mappings[2]).toEqual({ question: 'Q1', dimension: 'DELIVERY_STABILITY', weight: 1 });
    await userEvent.click(screen.getByRole('button', { name: 'Remove Q1 from Team health' }));
    expect(written().dimensions.mappings.map((m: { question: string }) => m.question)).toEqual(['Q2', 'Q1']);
  });

  it('adds and removes a choice, and keeps at least two', async () => {
    render(<Harness initial={base()} />);
    await open('Questions');
    await userEvent.type(screen.getByLabelText('New choice (Q2)'), 'rarely');
    await userEvent.click(screen.getByRole('button', { name: 'Add choice' }));
    expect(written().questions[1].normalization.values.RARELY).toBe(50);
    await userEvent.click(screen.getByRole('button', { name: 'Remove choice Never of Q2' }));
    expect(Object.keys(written().questions[1].normalization.values)).toEqual(['ALWAYS', 'SOMETIMES', 'RARELY']);
  });

  it('adds a question with starting values, refusing an id already taken', async () => {
    render(<Harness initial={base()} />);
    await open('Questions');
    const id = screen.getByLabelText('Question id');
    await userEvent.clear(id);
    await userEvent.type(id, 'Q1');
    expect(screen.getByText('There is already a question with this id.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add question' })).toBeDisabled();
    await userEvent.clear(id);
    await userEvent.type(id, 'NEW1');
    await userEvent.selectOptions(screen.getByLabelText('Kind'), 'BOOLEAN');
    await userEvent.click(screen.getByRole('button', { name: 'Add question' }));
    const added = written().questions.at(-1);
    expect(added).toEqual({ id: 'NEW1', type: 'BOOLEAN', normalization: { trueValue: 100, falseValue: 0 }, missingPolicy: 'IGNORE' });
  });

  it('removes a question with its mappings only when nothing else refers to it', async () => {
    render(<Harness initial={base()} />);
    await open('Questions');
    // Q1 feeds signal S1: blocked, and says why
    expect(screen.getByRole('button', { name: 'Remove the question Q1' })).toBeDisabled();
    expect(screen.getAllByText(/Still used by signal S1/).length).toBeGreaterThan(0);
    // Q4 is not used at all
    await userEvent.click(screen.getByRole('button', { name: 'Remove the question Q4' }));
    expect(written().questions.map((q: { id: string }) => q.id)).toEqual(['Q1', 'Q2', 'Q3']);
  });

  it('sets and clears how group answers are combined for one question', async () => {
    render(<Harness initial={base()} />);
    await open('Questions');
    await userEvent.selectOptions(screen.getByLabelText('Group answers combined by (Q1)'), 'MEAN');
    expect(written().questions[0].aggregation).toBe('MEAN');
    await userEvent.selectOptions(screen.getByLabelText('Group answers combined by (Q1)'), '');
    expect('aggregation' in written().questions[0]).toBe(false);
  });
});

describe('signal forms', () => {
  it('changes which questions a signal comes from and its reliability', async () => {
    render(<Harness initial={base()} />);
    await open('Signals');
    const group = screen.getByRole('group', { name: 'Questions behind S1' });
    await userEvent.click(within(group).getByRole('checkbox', { name: 'Q3' }));
    expect(written().signals[0].sources).toEqual(['Q1', 'Q3']);
    await userEvent.click(within(group).getByRole('checkbox', { name: 'Q1' }));
    expect(written().signals[0].sources).toEqual(['Q3']);
    const reliability = screen.getByLabelText('Reliability (S2)');
    await userEvent.clear(reliability);
    await userEvent.type(reliability, '0.5');
    expect(written().signals[1].reliability).toBe(0.5);
  });

  it('adds a signal and removes one that no pattern uses', async () => {
    render(<Harness initial={base()} />);
    await open('Signals');
    expect(screen.getByRole('button', { name: 'Remove the signal S1' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Remove the signal UNUSED' }));
    expect(written().signals.map((s: { id: string }) => s.id)).toEqual(['S1', 'S2']);
    await userEvent.type(screen.getByLabelText('Signal id'), 's1');
    expect(screen.getByText('There is already a signal with this id.')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Signal id'));
    await userEvent.type(screen.getByLabelText('Signal id'), 'TEAM_MOOD');
    await userEvent.click(screen.getByRole('button', { name: 'Add signal' }));
    expect(written().signals.at(-1)).toEqual({ id: 'TEAM_MOOD', dataType: 'NUMBER', sources: ['Q1'], derivationVersion: '1' });
  });
});

describe('pattern forms', () => {
  it('edits severity, evidence, switches and advice', async () => {
    render(<Harness initial={base()} />);
    await open('Patterns');
    await userEvent.click(screen.getByText(/BURNOUT/));
    await userEvent.selectOptions(screen.getByLabelText('Severity (P1)'), 'CRITICAL');
    const least = screen.getByLabelText('Least confidence (P1)');
    await userEvent.clear(least);
    await userEvent.type(least, '0.9');
    await userEvent.click(screen.getByRole('switch', { name: 'Enabled (P1)' }));
    await userEvent.click(within(screen.getByRole('group', { name: 'Advice given with P1' })).getByRole('checkbox', { name: 'OTHER' }));
    const p = written().patterns[0];
    expect(p.severity).toBe('CRITICAL');
    expect(p.evidence).toEqual({ minimumSignals: 2, minimumConfidence: 0.9 });
    expect(p.enabled).toBe(false);
    expect(p.actions).toEqual(['REVIEW', 'OTHER']);
  });

  it('shows the condition as a sentence and edits the tree', async () => {
    render(<Harness initial={base()} />);
    await open('Patterns');
    expect(screen.getByText('(S1 is at most 25 and TECHNICAL_RISK is above 50)')).toBeInTheDocument();
    await userEvent.click(screen.getByText(/BURNOUT/));
    const value = screen.getByLabelText('Value', { selector: '#pat-0-cond-0-value' });
    await userEvent.clear(value);
    await userEvent.type(value, '10');
    expect(written().patterns[0].condition.conditions[0]).toEqual({ operator: 'LTE', signal: 'S1', value: 10 });
    // switch the second comparison to a range, then to a gap
    await userEvent.selectOptions(document.getElementById('pat-0-cond-1-op') as HTMLSelectElement, 'BETWEEN');
    expect(written().patterns[0].condition.conditions[1]).toEqual({ operator: 'BETWEEN', dimension: 'TECHNICAL_RISK', low: 0, high: 100 });
    await userEvent.selectOptions(document.getElementById('pat-0-cond-1-op') as HTMLSelectElement, 'GAP_GT');
    expect(written().patterns[0].condition.conditions[1]).toMatchObject({ operator: 'GAP_GT', left: { dimension: 'TECHNICAL_RISK' }, direction: 'LEFT_MINUS_RIGHT' });
  });

  it('adds and removes a condition inside a group, and turns the group into any-of', async () => {
    render(<Harness initial={base()} />);
    await open('Patterns');
    await userEvent.click(screen.getByText(/BURNOUT/));
    await userEvent.click(screen.getByRole('button', { name: 'Add a condition' }));
    expect(written().patterns[0].condition.conditions).toHaveLength(3);
    await userEvent.click(screen.getAllByRole('button', { name: 'Remove this condition' })[2]!);
    expect(written().patterns[0].condition.conditions).toHaveLength(2);
    await userEvent.selectOptions(document.getElementById('pat-0-cond-op') as HTMLSelectElement, 'OR');
    expect(written().patterns[0].condition.operator).toBe('OR');
    expect(written().patterns[0].condition.conditions).toHaveLength(2);
    // a list of values is typed with commas
    await userEvent.selectOptions(document.getElementById('pat-0-cond-0-op') as HTMLSelectElement, 'IN');
    const values = screen.getByLabelText('Values');
    await userEvent.clear(values);
    await userEvent.type(values, '10, 20');
    expect(written().patterns[0].condition.conditions[0].values).toEqual([10, 20]);
  });
});

describe('imbalance forms', () => {
  it('edits the two dimensions, the threshold and the severity', async () => {
    render(<Harness initial={base()} />);
    await open('Imbalance');
    await userEvent.selectOptions(screen.getByLabelText('First dimension (GAP)'), 'TEAM_HEALTH');
    await userEvent.selectOptions(screen.getByLabelText('Flag when the gap is (GAP)'), 'GAP_GT');
    const points = screen.getByLabelText('Points (GAP)');
    await userEvent.clear(points);
    await userEvent.type(points, '25');
    await userEvent.selectOptions(screen.getByLabelText('Severity (GAP)'), 'MEDIUM');
    expect(written().imbalances[0]).toMatchObject({ left: 'TEAM_HEALTH', operator: 'GAP_GT', threshold: 25, severity: 'MEDIUM' });
  });
});

describe('the real default configuration', () => {
  it.skipIf(!platformDefault)('shows every question, signal, pattern and rule, and a form that changes nothing leaves the document as it was', async () => {
    if (!platformDefault) return;
    const text = JSON.stringify(platformDefault, null, 2);
    function Real() {
      const [t, setT] = useState(text);
      return (
        <>
          <ConfigForms text={t} onChange={setT} readOnly={false} />
          <pre data-testid="document">{t}</pre>
        </>
      );
    }
    render(<Real />);
    await open('Questions');
    expect(screen.getAllByRole('group', { name: /Scale|Number|Yes or no|Choice/ }).length).toBe(platformDefault.questions.length);
    await open('Signals');
    expect(screen.getAllByRole('group', { name: /^Questions behind / }).length).toBe(platformDefault.signals.length);
    await open('Patterns');
    expect(screen.getAllByText(/^TH-001|^DS-|^TR-|^LL-|^XD-/).length).toBeGreaterThan(0);
    expect(document.querySelectorAll('details').length).toBe(platformDefault.patterns.length);
    await open('Imbalance');
    expect(screen.getAllByRole('group', { name: /DELIVERY_TECHNICAL_GAP/ }).length).toBe(1);
    expect(written()).toEqual(platformDefault);
  });
});
