import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ConfigForms } from './ConfigForms';

const band = (min: number, max: number, label: string) => ({ min, max, label });
const base = () => ({
  overall: {
    weights: { TEAM_HEALTH: 0.5, DELIVERY_STABILITY: 0.5 },
    tolerance: 0.0001,
    lisInterpretation: [band(0, 50, 'AT_RISK'), band(50, 100, 'HEALTHY')],
    dimensionInterpretation: [band(0, 100, 'ALL')],
  },
  dimensions: { settings: { TEAM_HEALTH: { requiredCoverage: 0.75 } }, mappings: [] },
  responses: { defaultMethod: 'MEDIAN', minResponses: 5, signalShare: 0.5, fullConfidenceResponses: 10 },
  penalties: [
    {
      id: 'TH-P01',
      code: 'BURNOUT_RISK_PENALTY',
      trigger: { pattern: 'TH-001', version: 1 },
      target: 'TEAM_HEALTH',
      strategy: { type: 'SEVERITY_BASED', baseDeduction: 12, multipliers: { LOW: 0, HIGH: 0.7 }, notes: ['x'] },
      maximumDeduction: 12,
      stackingPolicy: 'CAPPED_STACK',
      maximumCumulativeDeduction: 30,
      priority: 10,
      enabled: true,
    },
  ],
  recommendations: [{ key: 'REVIEW_TEAM_CAPACITY', title: 'Review team capacity', description: 'Compare the load.', priority: 10 }],
  presentation: { dimensions: { TEAM_HEALTH: { name: 'Team Health', description: 'How healthy.' } } },
});

/** Holds the text the way the version page does, and shows it so a test can read what the forms wrote. */
function Harness({ initial, readOnly = false }: { initial: unknown; readOnly?: boolean }) {
  const [text, setText] = useState(typeof initial === 'string' ? initial : JSON.stringify(initial, null, 2));
  return (
    <>
      <ConfigForms text={text} onChange={setText} readOnly={readOnly} />
      <pre data-testid="document">{text}</pre>
    </>
  );
}
const written = () => JSON.parse(screen.getByTestId('document').textContent ?? '');

describe('ConfigForms', () => {
  it('shows the weights, says when they do not add up, and writes the change to the document', async () => {
    render(<Harness initial={base()} />);
    const weight = screen.getByLabelText('Team health');
    expect(screen.getByText(/Together: 1 \(must be 1\)/)).toBeInTheDocument();
    await userEvent.clear(weight);
    await userEvent.type(weight, '0.7');
    expect(written().overall.weights.TEAM_HEALTH).toBe(0.7);
    expect(await screen.findByText(/add up to 1.2/)).toBeInTheDocument();
    expect(Object.keys(written().overall.weights)).toEqual(['TEAM_HEALTH', 'DELIVERY_STABILITY']);
  });

  it('keeps what is typed while the text is not yet a number', async () => {
    render(<Harness initial={base()} />);
    const tolerance = screen.getByLabelText('Rounding tolerance for the weights');
    await userEvent.clear(tolerance);
    expect(tolerance).toHaveValue(null);
    await userEvent.type(tolerance, '0.01');
    expect(written().overall.tolerance).toBe(0.01);
  });

  it('colours each band by its level of risk and draws the 0 to 100 scale', () => {
    const doc = base();
    doc.overall.lisInterpretation = [band(0, 40, 'CRITICAL'), band(40, 60, 'AT_RISK'), band(60, 100, 'HEALTHY')];
    doc.overall.dimensionInterpretation = [band(0, 30, 'LOW_BAND'), band(30, 100, 'TOP_BAND')];
    render(<Harness initial={doc} />);
    expect(screen.getByText('Critical').className).toContain('text-risk-critical');
    expect(screen.getByText('At risk').className).toContain('text-risk-high');
    expect(screen.getByText('Healthy').className).toContain('text-ok');
    // a label the colours do not know is coloured by where it sits: the lowest band is the worst
    expect(screen.getByText('Low band').className).toContain('text-risk-critical');
    expect(screen.getByText('Top band').className).toContain('text-ok');
    expect(screen.getByRole('img', { name: /Score scale from 0 to 100: Critical 0 to 40, At risk 40 to 60, Healthy 60 to 100/ })).toBeInTheDocument();
  });

  it('flags bands that leave a gap', async () => {
    render(<Harness initial={base()} />);
    const from = screen.getAllByLabelText('From')[1]!;
    await userEvent.clear(from);
    await userEvent.type(from, '60');
    expect(written().overall.lisInterpretation[1].min).toBe(60);
    expect(await screen.findByText('Bands must follow each other with no gap and no overlap.')).toBeInTheDocument();
  });

  it('edits coverage and the way group answers are combined', async () => {
    render(<Harness initial={base()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Coverage and group answers' }));
    await userEvent.selectOptions(screen.getByLabelText('Combine by'), 'MEAN');
    const coverage = screen.getByLabelText('Team health');
    await userEvent.clear(coverage);
    await userEvent.type(coverage, '1.5');
    expect(written().responses.defaultMethod).toBe('MEAN');
    expect(written().dimensions.settings.TEAM_HEALTH.requiredCoverage).toBe(1.5);
    expect(screen.getByText('Use a value from 0 to 1.')).toBeInTheDocument();
  });

  it('edits a penalty: its amounts, multipliers, cap, stacking and whether it is on', async () => {
    render(<Harness initial={base()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Penalties' }));
    expect(screen.getByText(/Applies to Team health when pattern TH-001 is detected/)).toBeInTheDocument();
    const base12 = screen.getByLabelText('Base deduction');
    await userEvent.clear(base12);
    await userEvent.type(base12, '9');
    const high = screen.getByLabelText('Multipliers: High');
    await userEvent.clear(high);
    await userEvent.type(high, '0.5');
    await userEvent.selectOptions(screen.getByLabelText('Stacking'), 'MAX');
    await userEvent.click(screen.getByRole('switch', { name: /Enabled/ }));
    const penalty = written().penalties[0];
    expect(penalty.strategy.baseDeduction).toBe(9);
    expect(penalty.strategy.multipliers).toEqual({ LOW: 0, HIGH: 0.5 });
    expect(penalty.stackingPolicy).toBe('MAX');
    expect(penalty.enabled).toBe(false);
    // what the form does not edit is untouched, and said to be for the JSON view
    expect(penalty.strategy.notes).toEqual(['x']);
    expect(penalty.trigger).toEqual({ pattern: 'TH-001', version: 1 });
    expect(screen.getByText(/Notes is edited in the JSON view/)).toBeInTheDocument();
  });

  it('edits the advice and the display texts', async () => {
    render(<Harness initial={base()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Advice' }));
    const title = screen.getByLabelText('Title (REVIEW_TEAM_CAPACITY)');
    await userEvent.clear(title);
    await userEvent.type(title, 'Check capacity');
    expect(written().recommendations[0].title).toBe('Check capacity');
    await userEvent.click(screen.getByRole('button', { name: 'Display texts' }));
    await userEvent.click(screen.getByText(/Dimensions \(1\)/));
    const name = screen.getByLabelText('Name (TEAM_HEALTH)');
    await userEvent.clear(name);
    await userEvent.type(name, 'Team wellbeing');
    expect(written().presentation.dimensions.TEAM_HEALTH.name).toBe('Team wellbeing');
  });

  it('says a section is inherited when the version does not have it, as an override often does', async () => {
    render(<Harness initial={{}} />);
    expect(screen.getByText(/no overall score settings of its own/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Penalties' }));
    expect(screen.getByText(/no penalties of its own/)).toBeInTheDocument();
  });

  it('is read only when the version cannot be edited', async () => {
    render(<Harness initial={base()} readOnly />);
    expect(screen.getByLabelText('Team health')).toBeDisabled();
  });

  it('asks for the JSON to be fixed when it is not a document', () => {
    render(<Harness initial='{ "overall": ' />);
    expect(screen.getByRole('alert')).toHaveTextContent('Fix it there first');
  });
});
