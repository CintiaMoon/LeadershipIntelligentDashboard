import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import { renderRoutes } from '@/tests/utils';
import { PatternDetailPage } from './PatternDetailPage';

const server = installMockApi();
const routes = [{ path: '/runs/:runId/patterns/:patternId', element: <PatternDetailPage /> }];
const route = (id: string) => `/runs/${f.RUN_ID}/patterns/${id}`;

const withCondition = {
  ...f.patternsResponse,
  patterns: f.patternsResponse.patterns.map((p) =>
    p.patternId === 'TH-001'
      ? {
          ...p,
          conditionResult: {
            operator: 'AND',
            outcome: 'TRUE',
            children: [
              { operator: 'GTE', outcome: 'TRUE', detail: 'signal:WORKLOAD_PRESSURE=80.0000000000 GTE 75' },
              { operator: 'LTE', outcome: 'TRUE', detail: 'signal:TEAM_SUSTAINABILITY=25.0000000000 LTE 35' },
            ],
          },
          confidenceBreakdown: { coverage: 1, independence: 1, reliability: 0.8, freshness: 1, independentGroups: 2, supportingSignals: 2, algorithmVersion: '1' },
        }
      : p,
  ),
};

describe('PatternDetailPage', () => {
  it('explains why the pattern was detected in plain language, from the configured explanation', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json(withCondition)));
    renderRoutes(routes, { route: route('TH-001') });
    expect(await screen.findByRole('heading', { name: 'Team burnout risk', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Why this pattern was detected' })).toBeInTheDocument();
    expect(screen.getByText('High workload and low sustainability were reported together.')).toBeInTheDocument();
    expect(screen.getByText('All conditions were met.')).toBeInTheDocument();
  });

  it('shows severity and confidence as separate facts', async () => {
    renderRoutes(routes, { route: route('TH-001') });
    await screen.findByRole('heading', { name: 'Team burnout risk', level: 1 });
    expect(screen.getByText('Severity (impact)').nextElementSibling).toHaveTextContent('Critical');
    expect(screen.getByText('Confidence (evidence quality)').nextElementSibling).toHaveTextContent('Strong');
    expect(screen.getByText('Affected dimensions').nextElementSibling).toHaveTextContent('Team Health');
  });

  it('lists each matched condition in readable words with whether it was met', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json(withCondition)));
    renderRoutes(routes, { route: route('TH-001') });
    const tree = await screen.findByRole('list', { name: 'Matched conditions' });
    expect(within(tree).getByText('All of these must be met')).toBeInTheDocument();
    expect(within(tree).getByText('Workload pressure is at least 75 (it was 80.00)')).toBeInTheDocument();
    expect(within(tree).getByText('Team sustainability is at most 35 (it was 25.00)')).toBeInTheDocument();
    expect(within(tree).getAllByText('Met').length).toBe(3);
  });

  it('shows the evidence and how confidence was determined', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json(withCondition)));
    renderRoutes(routes, { route: route('TH-001') });
    await screen.findByRole('heading', { name: 'Evidence' });
    expect(screen.getByText('80.00')).toBeInTheDocument();
    expect(screen.getByText('How confidence was determined')).toBeInTheDocument();
  });

  it('links the penalty it triggered, with its state, and the actions it recommends', async () => {
    renderRoutes(routes, { route: route('DS-001') });
    const penalty = await screen.findByRole('link', { name: 'Delivery slippage deduction' });
    expect(penalty).toHaveAttribute('href', `/runs/${f.RUN_ID}/penalties/DS-P01`);
    expect(penalty.closest('div')?.parentElement).toHaveTextContent('Suppressed');
    expect(screen.getByText('Stabilize delivery')).toBeInTheDocument();
  });

  it('lists related patterns in the same category or sharing evidence', async () => {
    renderRoutes(routes, { route: route('TH-001') });
    const card = (await screen.findByRole('heading', { name: 'Related patterns' })).closest('section') as HTMLElement;
    // DS-001 and XD-001 rest on the same two signals; TR-001 was not detected, so it is not related
    expect(within(card).getByRole('link', { name: 'Delivery slippage' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/patterns/DS-001`);
    expect(within(card).queryByRole('link', { name: 'Release risk' })).not.toBeInTheDocument();
  });

  it('explains why a pattern without enough evidence was not treated as a finding', async () => {
    renderRoutes(routes, { route: route('TR-001') });
    expect(await screen.findByRole('heading', { name: 'Why this was not treated as a finding' })).toBeInTheDocument();
    expect(screen.getByText(/Confidence is below the configured minimum/)).toBeInTheDocument();
    expect(screen.getAllByText('Ineligible').length).toBeGreaterThan(0);
  });

  it('lists missing data for a condition that could not be evaluated', async () => {
    const missing = { ...f.patternsResponse, patterns: f.patternsResponse.patterns.map((p) => (p.patternId === 'TR-001' ? { ...p, missingOperands: ['signal:WORKLOAD_PRESSURE'] } : p)) };
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json(missing)));
    renderRoutes(routes, { route: route('TR-001') });
    expect(await screen.findByText(/Missing data: Workload pressure/)).toBeInTheDocument();
  });

  it('hides evidence values from a viewer', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json(f.redactedPatternsResponse)));
    renderRoutes(routes, { route: route('TH-001') });
    expect(await screen.findByText(/Values are hidden for your role/)).toBeInTheDocument();
    expect(screen.queryByText('80.00')).not.toBeInTheDocument();
  });

  it('reports an unknown pattern', async () => {
    renderRoutes(routes, { route: route('NOPE') });
    expect(await screen.findByText('Pattern not found')).toBeInTheDocument();
  });
});
