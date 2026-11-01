import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { useSession } from '@/stores/session';
import * as f from '@/tests/fixtures';
import { ORIGIN, dashboardHandlers } from '@/tests/msw';
import * as s from '@/tests/scoped';
import { renderRoutes } from '@/tests/utils';
import { DashboardPage } from './DashboardPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/** The organization run was scored from member answers; each team has its own run, served with the same finding data. */
function scopedHandlers() {
  const teamRoutes = (id: string, run: typeof s.teamRun) => [
    http.get(`${ORIGIN}/scoring-runs/${id}`, () => HttpResponse.json(run)),
    http.get(`${ORIGIN}/scoring-runs/${id}/dimensions`, () => HttpResponse.json(f.dimensionsResponse)),
    http.get(`${ORIGIN}/scoring-runs/${id}/patterns`, () => HttpResponse.json(f.patternsResponse)),
    http.get(`${ORIGIN}/scoring-runs/${id}/penalties`, () => HttpResponse.json(f.penaltiesResponse)),
    http.get(`${ORIGIN}/scoring-runs/${id}/recommendations`, () => HttpResponse.json(f.recommendations)),
    http.get(`${ORIGIN}/scoring-runs/${id}/catalog`, () => HttpResponse.json(f.catalog)),
  ];
  // the first matching handler wins, so the overrides come before the defaults
  return [
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}`, () => HttpResponse.json(s.scopedRun)),
    ...teamRoutes(s.TEAM_RUN_ID, s.teamRun),
    ...teamRoutes(s.SMALL_TEAM_RUN_ID, s.smallTeamRun),
    ...dashboardHandlers(),
  ];
}

beforeEach(() => {
  configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined });
  useSession.setState({ organizationId: 'org-1' });
  server.use(...scopedHandlers());
});

const routes = [{ path: '/dashboard', element: <DashboardPage /> }];

describe('DashboardPage with answers from several people', () => {
  it('offers a scope switcher and explains how the answers were combined', async () => {
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByTestId('lis-value')).toHaveTextContent('67.00');
    expect(screen.getByRole('combobox', { name: 'Scope' })).toHaveValue('');
    expect(await screen.findByRole('heading', { name: 'How the answers were combined' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'How the answers of each question were combined' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Historical trends' })).toBeInTheDocument();
  });

  it('shows a team with its own score, and leaves out history and comparison, which exist for the organization only', async () => {
    renderRoutes(routes, { route: '/dashboard?team=alpha' });
    expect(await screen.findByTestId('lis-value')).toHaveTextContent('71.50');
    expect(screen.getByText(/Showing team alpha/)).toHaveTextContent('Teams are never ranked against each other');
    expect(screen.getByRole('combobox', { name: 'Scope' })).toHaveValue('alpha');
    expect(screen.queryByText('Compare with the previous assessment')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Historical trends' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Since the previous assessment/)).not.toBeInTheDocument();
    expect(screen.getByText(/Organization org-1/)).toHaveTextContent('team alpha');
    // the team's explanation, not the organization's
    expect(screen.getByRole('link', { name: 'How was this calculated?' })).toHaveAttribute('href', `/runs/${s.TEAM_RUN_ID}/explanation`);
  });

  it('switching the scope puts the team in the address', async () => {
    const view = renderRoutes(routes, { route: '/dashboard' });
    await screen.findByTestId('lis-value');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Scope' }), 'alpha');
    expect(view.router.state.location.search).toBe('?team=alpha');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Scope' }), '');
    expect(view.router.state.location.search).toBe('');
  });

  it('shows no score for a team with too few responses and says why, instead of a number', async () => {
    renderRoutes(routes, { route: '/dashboard?team=small' });
    expect(await screen.findByTestId('lis-value')).not.toHaveTextContent(/\d/);
    const note = (await screen.findAllByRole('note')).map((n) => n.textContent).join(' ');
    expect(note).toContain('2 questions had fewer than 5 responses');
    expect(note).toContain('no score is shown for this scope');
    expect(screen.getAllByText('Too few responses')).toHaveLength(2);
  });

  it('shows an explanation, not a blank page, for a team that could not be scored at all', async () => {
    renderRoutes(routes, { route: '/dashboard?team=tiny' });
    expect(await screen.findByRole('heading', { name: 'Not enough responses to score this team' })).toBeInTheDocument();
    expect(screen.queryByTestId('lis-value')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Back to the whole organization' }));
    expect(await screen.findByTestId('lis-value')).toHaveTextContent('67.00');
  });

  it('treats an unknown team in the address as the whole organization', async () => {
    renderRoutes(routes, { route: '/dashboard?team=ghost' });
    expect(await screen.findByTestId('lis-value')).toHaveTextContent('67.00');
    expect(screen.queryByText(/Showing team/)).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Scope' })).toHaveValue('');
  });

  it('has no switcher and no combination panel for an assessment scored from one answer per question', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}`, () => HttpResponse.json(f.run)));
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByTestId('lis-value')).toHaveTextContent('67.00');
    expect(screen.queryByRole('combobox', { name: 'Scope' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'How the answers were combined' })).not.toBeInTheDocument();
  });

  it('keeps the page working when the response summaries are in an unexpected shape', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}`, () => HttpResponse.json({ ...s.scopedRun, responses: { questions: [{ questionId: 1 }] } })));
    renderRoutes(routes, { route: '/dashboard' });
    expect(await screen.findByTestId('lis-value')).toHaveTextContent('67.00');
    expect(await screen.findByText('The data arrived in an unexpected format')).toBeInTheDocument();
  });

  it('puts the template switch back to the whole organization', async () => {
    const second = { ...f.summary, templateId: 'tpl-2', scoringRunId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' };
    server.use(
      http.get(`${ORIGIN}/organizations/org-1/dashboard`, () => HttpResponse.json({ ...f.dashboard, latestPerTemplate: [f.summary, second] })),
      http.get(`${ORIGIN}/scoring-runs/${second.scoringRunId}`, () => HttpResponse.json(f.run)),
      http.get(`${ORIGIN}/scoring-runs/${second.scoringRunId}/dimensions`, () => HttpResponse.json(f.dimensionsResponse)),
      http.get(`${ORIGIN}/scoring-runs/${second.scoringRunId}/patterns`, () => HttpResponse.json(f.patternsResponse)),
      http.get(`${ORIGIN}/scoring-runs/${second.scoringRunId}/penalties`, () => HttpResponse.json(f.penaltiesResponse)),
      http.get(`${ORIGIN}/scoring-runs/${second.scoringRunId}/recommendations`, () => HttpResponse.json(f.recommendations)),
      http.get(`${ORIGIN}/scoring-runs/${second.scoringRunId}/catalog`, () => HttpResponse.json(f.catalog)),
    );
    const view = renderRoutes(routes, { route: '/dashboard?team=alpha' });
    await screen.findByText(/Showing team alpha/);
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Assessment template' }), 'tpl-2');
    expect(view.router.state.location.search).toBe('?template=tpl-2');
    const header = screen.getByRole('heading', { level: 1 }).parentElement as HTMLElement;
    expect(within(header).queryByText(/team alpha/)).not.toBeInTheDocument();
  });
});
