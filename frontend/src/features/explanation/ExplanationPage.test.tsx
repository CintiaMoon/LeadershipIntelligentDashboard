import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import { renderRoutes } from '@/tests/utils';
import { ExplanationPage } from './ExplanationPage';

const server = installMockApi();
const routes = [{ path: '/runs/:runId/explanation', element: <ExplanationPage /> }];
const route = `/runs/${f.RUN_ID}/explanation`;

describe('ExplanationPage', () => {
  it('follows the calculation in eight numbered steps from inputs to the final score', async () => {
    renderRoutes(routes, { route });
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'How the Leadership Intelligence Score was calculated' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Calculation steps' });
    expect(within(nav).getAllByRole('link').map((l) => l.textContent)).toEqual([
      '1Assessment inputs',
      '2Normalization',
      '3Dimension contributions',
      '4Raw dimension scores',
      '5Pattern detection',
      '6Penalties',
      '7Adjusted dimensions',
      '8Final LIS',
    ]);
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(8);
  });

  it('shows the versions behind the run', async () => {
    renderRoutes(routes, { route });
    expect(await screen.findByText(/configuration platform_default:v1/)).toHaveTextContent('engine 3.0.0, algorithm 1.0.0');
  });

  it('shows normalized values with configured question names, clamping and the signals raised', async () => {
    renderRoutes(routes, { route });
    await screen.findByRole('heading', { name: 'Normalization' });
    const table = screen.getByRole('table', { name: 'Normalized answers' });
    expect(within(table).getByRole('row', { name: /Sustainable pace/ })).toHaveTextContent('25.00');
    expect(within(table).getByRole('row', { name: /Workload level/ })).toHaveTextContent('Clamped to range');
    expect(within(table).getByRole('row', { name: /Q04/ })).toHaveTextContent('Workload pressure');
    expect(screen.getByText('Signals derived from the answers')).toBeInTheDocument();
  });

  it('shows contributions with value, weight, share and contribution', async () => {
    renderRoutes(routes, { route });
    await screen.findByRole('heading', { name: 'Dimension contributions' });
    const table = screen.getByRole('table', { name: 'Contributions to Team Health' });
    expect(within(table).getByRole('row', { name: /Sustainable pace/ })).toHaveTextContent('25.00');
    expect(within(table).getByRole('row', { name: /Sustainable pace/ })).toHaveTextContent('100%');
  });

  it('lists detected patterns with links to their detail', async () => {
    renderRoutes(routes, { route });
    const link = await screen.findByRole('link', { name: 'Team burnout risk' });
    expect(link).toHaveAttribute('href', `/runs/${f.RUN_ID}/patterns/TH-001`);
    expect(screen.getByText(/3 of 5 configured patterns were detected/)).toBeInTheDocument();
  });

  it('lists suppressed penalties alongside applied ones and links to their detail', async () => {
    renderRoutes(routes, { route });
    const table = await screen.findByRole('table', { name: 'Penalties' });
    expect(within(table).getByRole('row', { name: /Delivery slippage deduction/ })).toHaveTextContent('Suppressed');
    expect(within(table).getByRole('row', { name: /Delivery slippage deduction/ })).toHaveTextContent('8.00');
    expect(within(table).getByRole('link', { name: 'Burnout risk deduction' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/penalties/TH-P01`);
    // the not-triggered penalty is not listed as a decision
    expect(within(table).queryByText(/Spillover/)).not.toBeInTheDocument();
  });

  it('shows raw, penalty, adjusted and weighted contribution per dimension', async () => {
    renderRoutes(routes, { route });
    const table = await screen.findByRole('table', { name: 'Raw to adjusted dimension scores' });
    const row = within(table).getByRole('row', { name: /Team Health/ });
    expect(row).toHaveTextContent('25.00');
    expect(row).toHaveTextContent('12.00');
    expect(row).toHaveTextContent('13.00');
    expect(row).toHaveTextContent('3.25');
    expect(row).toHaveTextContent('Critical');
  });

  it('ends with the waterfall, the final score and its interpretation', async () => {
    renderRoutes(routes, { route });
    const step = (await screen.findByRole('heading', { name: 'Final LIS' })).closest('details') as HTMLElement;
    expect(within(step).getByRole('img', { name: 'Final score 67.00' })).toBeInTheDocument();
    expect(within(step).getByText('Healthy')).toBeInTheDocument();
    expect(within(step).getByText(/band 60.80/)).toBeInTheDocument();
  });

  it('opens the steps that matter and lets the reader open the rest', async () => {
    renderRoutes(routes, { route });
    const normalization = (await screen.findByRole('heading', { name: 'Normalization' })).closest('details') as HTMLElement;
    expect(normalization).not.toHaveAttribute('open');
    await userEvent.click(within(normalization).getByText('Normalization'));
    expect(normalization).toHaveAttribute('open');
    expect((screen.getByRole('heading', { name: 'Final LIS' }).closest('details') as HTMLElement)).toHaveAttribute('open');
  });

  it('tells a viewer what is hidden while still showing the scores and findings', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/explanation`, () => HttpResponse.json(f.redactedTraceResponse)));
    renderRoutes(routes, { route });
    expect(await screen.findByRole('note')).toHaveTextContent('hidden for your role');
    expect(screen.getAllByText(/is hidden for your role/).length).toBeGreaterThan(0);
    expect(screen.queryByRole('table', { name: 'Normalized answers' })).not.toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Final score 67.00' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Raw to adjusted dimension scores' })).toBeInTheDocument();
  });

  it('shows a clear error with a retry for a transient failure', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/explanation`, () => HttpResponse.json({ code: 'X', message: 'boom', correlationId: 'e-1' }, { status: 503 })));
    renderRoutes(routes, { route });
    expect(await screen.findByRole('alert', undefined, { timeout: 4000 })).toHaveTextContent('temporarily unavailable');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('says when the score could not be calculated, without inventing one', async () => {
    const insufficient = { ...f.traceResponse, trace: { ...f.traceResponse.trace, steps: [{ stage: 'OVERALL', overall: { status: 'INSUFFICIENT_DATA', dimensions: [] } }] } };
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/explanation`, () => HttpResponse.json(insufficient)));
    renderRoutes(routes, { route });
    expect(await screen.findByText(/not enough data/)).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /Final score/ })).not.toBeInTheDocument();
  });

  it('reports an unexpected response instead of a blank page', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/explanation`, () => HttpResponse.json({ scoringRunId: f.RUN_ID, trace: { wrong: true } })));
    renderRoutes(routes, { route });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
