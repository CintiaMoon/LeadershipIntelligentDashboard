import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import { renderRoutes } from '@/tests/utils';
import { DimensionDetailPage } from './DimensionDetailPage';

const server = installMockApi();
const routes = [{ path: '/runs/:runId/dimensions/:dimension', element: <DimensionDetailPage /> }];
const route = (dimension: string) => `/runs/${f.RUN_ID}/dimensions/${dimension}`;
const viewer = { subject: 'v', tenantId: 'demo-tenant', organizations: ['org-1'], roles: ['VIEWER' as const] };

describe('DimensionDetailPage', () => {
  it('shows adjusted, raw and penalty values, the label and the configured band', async () => {
    renderRoutes(routes, { route: route('TEAM_HEALTH') });
    expect(await screen.findByRole('heading', { name: 'Team Health' })).toBeInTheDocument();
    expect(await screen.findByTestId('adjusted-score')).toHaveTextContent('13.00');
    expect(screen.getByText('Raw score').nextElementSibling).toHaveTextContent('25.00');
    expect(screen.getByText('Penalty applied').nextElementSibling).toHaveTextContent('12.00');
    expect(screen.getAllByText('Critical').length).toBeGreaterThan(0);
    expect(screen.getByText(/Band 0.40/)).toBeInTheDocument();
  });

  it('shows the change since the previous comparable assessment from the backend', async () => {
    const point = { ...f.trends.points?.[2], run: { ...f.summary, previousScoringRunId: f.PREVIOUS_RUN_ID, comparable: true } };
    server.use(http.get(`${ORIGIN}/organizations/org-1/trends`, () => HttpResponse.json({ ...f.trends, points: [f.trends.points?.[1], point] })));
    renderRoutes(routes, { route: route('TEAM_HEALTH') });
    await screen.findByTestId('adjusted-score');
    expect(await screen.findByText('+4.50')).toBeInTheDocument();
  });

  it('lists each question contribution with value, weight and contribution', async () => {
    renderRoutes(routes, { route: route('TEAM_HEALTH') });
    const table = await screen.findByRole('table', { name: 'Contributions to Team Health' });
    const row = within(table).getByRole('row', { name: /Sustainable pace/ });
    expect(row).toHaveTextContent('25.00');
    expect(row).toHaveTextContent('100%');
  });

  it('lets an authorized role open the source answer behind a contribution', async () => {
    renderRoutes(routes, { route: route('DELIVERY_STABILITY') });
    const table = await screen.findByRole('table', { name: 'Contributions to Delivery Stability' });
    await userEvent.click(within(table).getByRole('button', { name: 'Show answer' }));
    expect(await screen.findByText('OFTEN')).toBeInTheDocument();
    expect(screen.getByText(/recorded in the access log/)).toBeInTheDocument();
  });

  it('never offers or requests answers for a viewer', async () => {
    let requested = false;
    server.use(
      http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/answers`, () => {
        requested = true;
        return HttpResponse.json(f.answersResponse);
      }),
      http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/dimensions`, () => HttpResponse.json(f.dimensionsResponse)),
    );
    renderRoutes(routes, { route: route('TEAM_HEALTH'), auth: { principal: viewer } });
    await screen.findByRole('table', { name: 'Contributions to Team Health' });
    expect(screen.queryByRole('button', { name: 'Show answer' })).not.toBeInTheDocument();
    expect(screen.getByText(/Source answers are available to owners, administrators and analysts/)).toBeInTheDocument();
    expect(requested).toBe(false);
  });

  it('explains when question detail is hidden for the role', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/dimensions`, () => HttpResponse.json(f.redactedDimensionsResponse)));
    renderRoutes(routes, { route: route('TEAM_HEALTH'), auth: { principal: viewer } });
    expect(await screen.findByText('Question detail is not available')).toBeInTheDocument();
    expect(screen.getByText('It is hidden for your role.')).toBeInTheDocument();
  });

  it('links to the patterns and penalties of this dimension, suppressed ones included', async () => {
    renderRoutes(routes, { route: route('DELIVERY_STABILITY') });
    expect(await screen.findByRole('link', { name: 'Delivery slippage' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/patterns/DS-001`);
    const penalty = await screen.findByRole('link', { name: 'Delivery slippage deduction' });
    expect(penalty).toHaveAttribute('href', `/runs/${f.RUN_ID}/penalties/DS-P01`);
    expect(penalty.closest('div')?.parentElement).toHaveTextContent('Suppressed');
  });

  it('shows recommended actions that stem from this dimension patterns', async () => {
    renderRoutes(routes, { route: route('TEAM_HEALTH') });
    expect(await screen.findByText('Review team capacity')).toBeInTheDocument();
  });

  it('reports "not enough data" instead of a score for an insufficient dimension', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}`, () => HttpResponse.json({ ...f.run, dimensions: { ...f.run.dimensions, TEAM_HEALTH: { status: 'INSUFFICIENT_DATA', coverage: 0.5 } } })));
    renderRoutes(routes, { route: route('TEAM_HEALTH') });
    expect(await screen.findByText('Not enough data')).toBeInTheDocument();
    expect(screen.queryByTestId('adjusted-score')).not.toBeInTheDocument();
  });

  it('rejects an unknown dimension name', () => {
    renderRoutes(routes, { route: route('MORALE') });
    expect(screen.getByText('Unknown dimension')).toBeInTheDocument();
  });

  it('shows an error with a retry when the run cannot be loaded', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}`, () => HttpResponse.json({ code: 'X', message: 'x' }, { status: 503 })));
    renderRoutes(routes, { route: route('TEAM_HEALTH') });
    expect(await screen.findByRole('alert', undefined, { timeout: 4000 })).toHaveTextContent('temporarily unavailable');
  });
});
