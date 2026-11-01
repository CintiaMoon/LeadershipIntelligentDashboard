import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { useSession } from '@/stores/session';
import * as f from '@/tests/fixtures';
import { ORIGIN, dashboardHandlers } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import { DashboardPage } from './DashboardPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined });
  useSession.setState({ organizationId: 'org-1' });
  server.use(...dashboardHandlers());
});

const routes = [{ path: '/dashboard', element: <DashboardPage /> }];

describe('DashboardPage', () => {
  it('shows a loading state, then every section from the backend', async () => {
    renderRoutes(routes, { route: '/dashboard' });
    expect(screen.getByRole('status')).toBeInTheDocument();

    expect(await screen.findByTestId('lis-value')).toHaveTextContent('67.00');
    expect(await screen.findByRole('heading', { name: 'Dimensions' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Risk overview' })).toBeInTheDocument();
    expect(await screen.findByRole('article', { name: 'Team burnout risk' })).toBeInTheDocument();
    expect(await screen.findByRole('article', { name: 'Burnout risk deduction' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Review team capacity' })).toBeInTheDocument();
    expect((await screen.findAllByText(/3 assessments, oldest to newest/)).length).toBeGreaterThan(0);
    expect(screen.getByText('Version information')).toBeInTheDocument();
  });

  it('shows the four dimension cards with the comparison change from the backend', async () => {
    renderRoutes(routes, { route: '/dashboard' });
    for (const name of ['Team Health', 'Delivery Stability', 'Technical Risk', 'Leadership Load']) {
      expect(await screen.findByRole('heading', { name })).toBeInTheDocument();
    }
    await waitFor(() => expect(screen.getAllByText(/Since the previous assessment/)).not.toHaveLength(0));
    expect(screen.getByText('Penalties: 0 applied, 1 suppressed')).toBeInTheDocument();
  });

  it('filters patterns by clicking a severity count and clears the filter', async () => {
    renderRoutes(routes, { route: '/dashboard' });
    await userEvent.click(await screen.findByRole('button', { name: /High/ }));
    expect(await screen.findByText(/Showing High severity only/)).toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Team burnout risk' })).not.toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Delivery slippage' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear filter' }));
    expect(await screen.findByRole('article', { name: 'Team burnout risk' })).toBeInTheDocument();
  });

  it('restores the filter and grouping from the address', async () => {
    renderRoutes(routes, { route: '/dashboard?severity=CRITICAL&group=severity' });
    expect(await screen.findByText(/Showing Critical severity only/)).toBeInTheDocument();
    expect(screen.getByLabelText('Severity')).toBeChecked();
  });

  it('ignores an unknown severity in the address', async () => {
    renderRoutes(routes, { route: '/dashboard?severity=SPICY' });
    expect(await screen.findByRole('article', { name: 'Team burnout risk' })).toBeInTheDocument();
    expect(screen.queryByText(/severity only/)).not.toBeInTheDocument();
  });

  it('keeps working when one section fails and lets the user retry just that section', async () => {
    let calls = 0;
    server.use(
      http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/penalties`, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json({ code: 'X', message: 'boom', correlationId: 'pen-1' }, { status: 503 }) : HttpResponse.json(f.penaltiesResponse);
      }),
    );
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByTestId('lis-value')).toBeInTheDocument();
    const alert = await screen.findByRole('alert', undefined, { timeout: 4000 });
    expect(alert).toHaveTextContent('temporarily unavailable');
    expect(screen.getByRole('article', { name: 'Team burnout risk' })).toBeInTheDocument();
    await userEvent.click(within(alert).getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('article', { name: 'Burnout risk deduction' })).toBeInTheDocument();
  });

  it('shows a clear error, not a blank page, when a response has an unexpected shape', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json({ scoringRunId: f.RUN_ID, patterns: [{ patternId: 5 }] })));
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(await screen.findByTestId('lis-value')).toBeInTheDocument();
  });

  it('degrades to readable codes when the catalog is unavailable', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/catalog`, () => HttpResponse.json({ code: 'X', message: 'no' }, { status: 404 })));
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByRole('heading', { name: 'Team health' })).toBeInTheDocument();
    expect(await screen.findByRole('article', { name: 'Team burnout risk' })).toBeInTheDocument();
  });

  it('hides values and question detail from a viewer', async () => {
    server.use(
      http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/dimensions`, () => HttpResponse.json(f.redactedDimensionsResponse)),
      http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json(f.redactedPatternsResponse)),
    );
    renderRoutes(routes, { route: '/dashboard' });
    expect((await screen.findAllByText('Question-level detail is hidden for your role.')).length).toBe(4);
    await userEvent.click(await screen.findByRole('button', { name: 'Show evidence' }));
    expect(screen.getByText(/Values are hidden for your role/)).toBeInTheDocument();
  });

  it('offers a template switch when the organization has several templates', async () => {
    const other = { ...f.summary, scoringRunId: 'other-run', templateId: 'tpl-2', leadershipIntelligenceScore: 50 };
    server.use(http.get(`${ORIGIN}/organizations/org-1/dashboard`, () => HttpResponse.json({ ...f.dashboard, latestPerTemplate: [f.summary, other] })));
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByLabelText('Assessment template')).toHaveValue('tpl-1');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['tpl-1', 'tpl-2']);
  });

  it('asks for an assessment when nothing has been scored, and still shows the notice', async () => {
    server.use(http.get(`${ORIGIN}/organizations/org-1/dashboard`, () => HttpResponse.json({ ...f.dashboard, latestPerTemplate: [], recentRuns: [] })));
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByText('No scored assessments yet')).toBeInTheDocument();
    expect(screen.getByText(/organizational diagnostic signal/)).toBeInTheDocument();
  });

  it('shows the dashboard error with its reference and no retry for permission errors', async () => {
    server.use(http.get(`${ORIGIN}/organizations/org-1/dashboard`, () => HttpResponse.json({ code: 'ORGANIZATION_ACCESS_DENIED', message: 'no', correlationId: 'ref-9' }, { status: 403 })));
    renderRoutes(routes, { route: '/dashboard' });
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('do not have access');
    expect(alert).toHaveTextContent('ref-9');
    expect(within(alert).queryByRole('button')).not.toBeInTheDocument();
  });

  it('explains when the account has no organization', () => {
    useSession.setState({ organizationId: null });
    renderRoutes(routes, { route: '/dashboard' });
    expect(screen.getByText('No organization available')).toBeInTheDocument();
  });

  it('shows no comparison change when the latest run is not comparable', async () => {
    server.use(
      http.get(`${ORIGIN}/organizations/org-1/dashboard`, () =>
        HttpResponse.json({ ...f.dashboard, latestPerTemplate: [{ ...f.summary, comparable: false, change: undefined, notComparableReasons: ['CONFIGURATION_DIFFERS'] }] }),
      ),
    );
    renderRoutes(routes, { route: '/dashboard' });
    await screen.findByTestId('lis-value');
    expect(screen.queryByText(/Since the previous assessment/)).not.toBeInTheDocument();
    expect(screen.queryByText('+4.50')).not.toBeInTheDocument();
  });

  it('links to the explanation, the comparison and the detail pages', async () => {
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByRole('link', { name: 'How was this calculated?' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/explanation`);
    expect(screen.getByRole('link', { name: 'Compare with the previous assessment' })).toHaveAttribute('href', `/compare?base=${f.PREVIOUS_RUN_ID}&run=${f.RUN_ID}`);
    expect(await screen.findByRole('link', { name: 'Detailed analysis: Team Health' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/dimensions/TEAM_HEALTH`);
    expect(await screen.findByRole('link', { name: 'Pattern details: Team burnout risk' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/patterns/TH-001`);
    expect(await screen.findByRole('link', { name: 'Penalty details: Burnout risk deduction' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/penalties/TH-P01`);
  });
});
