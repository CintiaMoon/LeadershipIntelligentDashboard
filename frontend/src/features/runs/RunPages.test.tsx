import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { useSession } from '@/stores/session';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import type { AssessmentRunDetail, AssessmentRunSummary } from '@/types/api';
import { RunDetailPage } from './RunDetailPage';
import { RunListPage } from './RunListPage';
import { StartRunPage } from './StartRunPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined });
  useSession.setState({ organizationId: 'org-1' });
});

const run = (over: Partial<AssessmentRunSummary> = {}): AssessmentRunSummary => ({
  id: 'r-1',
  name: 'Q4 pulse',
  organizationId: 'org-1',
  questionnaireId: 'q-1',
  questionnaireName: 'Team pulse',
  questionnaireVersion: 2,
  status: 'OPEN',
  dueDate: '2026-12-01',
  createdAt: '2026-11-01T09:00:00Z',
  participants: 6,
  submitted: 4,
  ...over,
});
const detail = (over: Partial<AssessmentRunSummary> = {}): AssessmentRunDetail => ({
  run: run(over),
  teams: [
    { teamId: 't-a', teamName: 'Alpha', total: 4, notStarted: 1, inProgress: 0, submitted: 3 },
    { teamId: 't-b', teamName: 'Beta', total: 2, notStarted: 0, inProgress: 1, submitted: 1 },
  ],
  participants: [
    { teamId: 't-a', name: 'Ada Lovelace', email: 'ada@acme.test', status: 'SUBMITTED' },
    { teamId: 't-b', name: 'Grace Hopper', email: 'grace@acme.test', status: 'IN_PROGRESS' },
  ],
  notice: 'This list shows who has not finished. In a very small team that can let you work out who is missing.',
});

describe('RunListPage', () => {
  const render = () => renderRoutes([{ path: '/assessment-runs', element: <RunListPage /> }, { path: '/assessment-runs/:runId', element: <p>detail</p> }], { route: '/assessment-runs' });

  it('lists the runs with their state, progress and due date', async () => {
    server.use(http.get(`${ORIGIN}/runs`, () => HttpResponse.json([run(), run({ id: 'r-2', name: 'Q3 pulse', status: 'CLOSED', dueDate: undefined, submitted: 6 })])));
    render();
    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]!).getByText('Open')).toBeInTheDocument();
    expect(within(rows[0]!).getByText('4 of 6')).toBeInTheDocument();
    expect(within(rows[0]!).getByText('Team pulse')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('Closed')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('No due date')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('2 runs in org-1');
  });

  it('asks only for the runs of the chosen organization and status', async () => {
    const seen: string[] = [];
    server.use(
      http.get(`${ORIGIN}/runs`, ({ request }) => {
        seen.push(new URL(request.url).search);
        return HttpResponse.json([]);
      }),
    );
    render();
    await screen.findByText(/No runs yet/);
    await userEvent.click(screen.getByRole('button', { name: 'Open' }));
    await waitFor(() => expect(seen).toContain('?organizationId=org-1&status=OPEN'));
    expect(seen[0]).toBe('?organizationId=org-1');
  });

  it('asks for an organization first when none is chosen', () => {
    useSession.setState({ organizationId: null });
    render();
    expect(screen.getByText('Choose an organization')).toBeInTheDocument();
  });
});

describe('StartRunPage', () => {
  const serve = (questionnaires: unknown[], teams: unknown[]) =>
    server.use(http.get(`${ORIGIN}/questionnaires`, () => HttpResponse.json(questionnaires)), http.get(`${ORIGIN}/teams`, () => HttpResponse.json(teams)));
  const published = { id: 'q-1', name: 'Team pulse', state: 'PUBLISHED', archived: false, publishedVersion: 2 };
  const teams = [
    { id: 't-a', organizationId: 'org-1', name: 'Alpha', activePeople: 4 },
    { id: 't-b', organizationId: 'org-1', name: 'Beta', activePeople: 1 },
  ];
  const render = () => renderRoutes([{ path: '/assessment-runs/new', element: <StartRunPage /> }, { path: '/assessment-runs/:runId', element: <p>run detail</p> }], { route: '/assessment-runs/new' });

  it('offers only published questionnaires that are not archived', async () => {
    serve([published, { id: 'q-2', name: 'Draft one', state: 'DRAFT', archived: false }, { id: 'q-3', name: 'Old', state: 'ARCHIVED', archived: true, publishedVersion: 1 }, { ...published, id: 'q-4', name: 'With draft', state: 'PUBLISHED_WITH_DRAFT', publishedVersion: 1 }], teams);
    render();
    const select = await screen.findByLabelText('Questionnaire');
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual(['Choose...', 'Team pulse (v2)', 'With draft (v1)']);
  });

  it('starts the run with the chosen questionnaire, teams and due date, then opens it', async () => {
    serve([published], teams);
    let body: unknown;
    server.use(
      http.post(`${ORIGIN}/runs`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(detail(), { status: 201 });
      }),
    );
    render();
    await userEvent.type(await screen.findByLabelText('Name'), 'Q4 pulse');
    await userEvent.selectOptions(screen.getByLabelText('Questionnaire'), 'q-1');
    await userEvent.click(screen.getByRole('checkbox', { name: /Alpha/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /Beta/ }));
    await userEvent.type(screen.getByLabelText('Due date (optional)'), '2026-12-01');
    await userEvent.click(screen.getByRole('button', { name: 'Start run' }));
    expect(await screen.findByText('run detail')).toBeInTheDocument();
    expect(body).toEqual({ name: 'Q4 pulse', organizationId: 'org-1', questionnaireId: 'q-1', teamIds: ['t-a', 't-b'], dueDate: '2026-12-01' });
  });

  it('says what is missing without sending anything', async () => {
    serve([published], teams);
    let requests = 0;
    server.use(
      http.post(`${ORIGIN}/runs`, () => {
        requests += 1;
        return HttpResponse.json(detail(), { status: 201 });
      }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Start run' }));
    expect(screen.getByText('Enter a name for the run.')).toBeInTheDocument();
    expect(screen.getByText('Choose a published questionnaire.')).toBeInTheDocument();
    expect(screen.getByText('Choose at least one team.')).toBeInTheDocument();
    expect(requests).toBe(0);
  });

  it('shows the reason when the server refuses', async () => {
    serve([published], teams);
    server.use(http.post(`${ORIGIN}/runs`, () => HttpResponse.json({ code: 'RUN_INVALID', message: 'the teams have no active people to ask' }, { status: 422 })));
    render();
    await userEvent.type(await screen.findByLabelText('Name'), 'Q4');
    await userEvent.selectOptions(screen.getByLabelText('Questionnaire'), 'q-1');
    await userEvent.click(screen.getByRole('checkbox', { name: /Alpha/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Start run' }));
    expect(await screen.findByText(/the teams have no active people to ask/)).toBeInTheDocument();
  });

  it('explains what to do first when nothing is published or there is no team', async () => {
    serve([{ id: 'q-2', name: 'Draft', state: 'DRAFT', archived: false }], teams);
    render();
    expect(await screen.findByText('No published questionnaire')).toBeInTheDocument();
  });

  it('explains it when the organization has no team yet', async () => {
    serve([published], []);
    render();
    expect(await screen.findByText('No teams in this organization')).toBeInTheDocument();
  });
});

describe('RunDetailPage', () => {
  const render = () => renderRoutes([{ path: '/assessment-runs/:runId', element: <RunDetailPage /> }], { route: '/assessment-runs/r-1' });

  it('shows progress per team and who has finished, with the notice about small teams', async () => {
    server.use(http.get(`${ORIGIN}/runs/r-1`, () => HttpResponse.json(detail())));
    render();
    expect(await screen.findByRole('heading', { level: 1, name: 'Q4 pulse' })).toBeInTheDocument();
    expect(screen.getByText(/4 of 6 finished/)).toBeInTheDocument();
    const progress = screen.getByRole('table', { name: 'Progress by team' });
    expect(within(progress).getByText('Alpha').closest('tr')).toHaveTextContent('1');
    const people = screen.getByRole('table', { name: /People asked to answer/ });
    expect(within(people).getByText('Ada Lovelace').closest('tr')).toHaveTextContent('Submitted');
    expect(within(people).getByText('Grace Hopper').closest('tr')).toHaveTextContent('In progress');
    expect(screen.getByRole('note')).toHaveTextContent('work out who is missing');
  });

  it('closes an open run only after a confirmation that says how many have not finished', async () => {
    server.use(http.get(`${ORIGIN}/runs/r-1`, () => HttpResponse.json(detail())));
    let closed = false;
    server.use(
      http.post(`${ORIGIN}/runs/r-1/close`, () => {
        closed = true;
        server.use(http.get(`${ORIGIN}/runs/r-1`, () => HttpResponse.json(detail({ status: 'CLOSED', closedAt: '2026-11-02T09:00:00Z', scoringRunId: 'sr-1' }))));
        return HttpResponse.json(detail({ status: 'CLOSED', closedAt: '2026-11-02T09:00:00Z', scoringRunId: 'sr-1' }));
      }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Close and score' }));
    expect(screen.getByText('1 of 2 people have not finished. Close the run anyway?')).toBeInTheDocument();
    expect(closed).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: 'Close the run' }));
    expect(await screen.findByRole('heading', { name: 'Result' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'How the score was calculated' })).toHaveAttribute('href', '/runs/sr-1/explanation');
    expect(screen.queryByRole('button', { name: 'Close and score' })).not.toBeInTheDocument();
  });

  it('lets the administrator keep the run open', async () => {
    server.use(http.get(`${ORIGIN}/runs/r-1`, () => HttpResponse.json(detail())));
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Close and score' }));
    await userEvent.click(screen.getByRole('button', { name: 'Keep it open' }));
    expect(screen.getByRole('button', { name: 'Close and score' })).toBeInTheDocument();
  });

  it('shows why a run could not be closed', async () => {
    server.use(
      http.get(`${ORIGIN}/runs/r-1`, () => HttpResponse.json(detail({ submitted: 0 }))),
      http.post(`${ORIGIN}/runs/r-1/close`, () => HttpResponse.json({ code: 'RUN_STATE', message: 'nobody has submitted an answer yet, so there is nothing to score' }, { status: 409 })),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Close and score' }));
    await userEvent.click(screen.getByRole('button', { name: 'Close the run' }));
    expect(await screen.findByText(/nobody has submitted an answer yet/)).toBeInTheDocument();
  });

  it('shows a closed run with the way to its result and no way to close it again', async () => {
    server.use(http.get(`${ORIGIN}/runs/r-1`, () => HttpResponse.json(detail({ status: 'CLOSED', closedAt: '2026-11-02T09:00:00Z', scoringRunId: 'sr-9' }))));
    render();
    expect(await screen.findByRole('heading', { name: 'Result' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the dashboard' })).toHaveAttribute('href', '/dashboard');
    expect(screen.queryByRole('button', { name: 'Close and score' })).not.toBeInTheDocument();
  });

  it('shows a run of another tenant as not found', async () => {
    server.use(http.get(`${ORIGIN}/runs/r-1`, () => HttpResponse.json({ code: 'RUN_NOT_FOUND', message: 'run not found' }, { status: 404 })));
    render();
    expect(await screen.findByText('Not found')).toBeInTheDocument();
  });
});
