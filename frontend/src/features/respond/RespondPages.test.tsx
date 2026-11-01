import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import type { RespondentRunDetail } from '@/types/api';
import { MyRunsPage } from './MyRunsPage';
import { RespondPage, isAnswered } from './RespondPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined }));

const respondent = { subject: 'r-1', tenantId: 'demo-tenant', organizations: [], roles: [], respondent: true };

describe('MyRunsPage', () => {
  const render = () => renderRoutes([{ path: '/respond', element: <MyRunsPage /> }, { path: '/respond/:runId', element: <p>form</p> }], { route: '/respond', auth: { principal: respondent } });

  it('lists the runs with a word for each state and the right action', async () => {
    server.use(
      http.get(`${ORIGIN}/respond/runs`, () =>
        HttpResponse.json([
          { runId: 'r-1', name: 'Q4 pulse', questionnaireName: 'Team pulse', dueDate: '2026-12-01', runStatus: 'OPEN', myStatus: 'NOT_STARTED' },
          { runId: 'r-2', name: 'Q3 pulse', questionnaireName: 'Team pulse', runStatus: 'OPEN', myStatus: 'IN_PROGRESS' },
          { runId: 'r-3', name: 'Q2 pulse', questionnaireName: 'Team pulse', runStatus: 'CLOSED', myStatus: 'SUBMITTED' },
          { runId: 'r-4', name: 'Q1 pulse', questionnaireName: 'Team pulse', runStatus: 'CLOSED', myStatus: 'NOT_STARTED' },
        ]),
      ),
    );
    render();
    const list = await screen.findByRole('list', { name: 'Your assessments' });
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(4);
    expect(within(items[0]!).getByText('Not started')).toBeInTheDocument();
    expect(within(items[0]!).getByRole('link', { name: 'Start Q4 pulse' })).toHaveAttribute('href', '/respond/r-1');
    expect(within(items[1]!).getByRole('link', { name: 'Continue Q3 pulse' })).toBeInTheDocument();
    expect(within(items[1]!).getByText('No due date')).toBeInTheDocument();
    expect(within(items[2]!).getByText('Submitted')).toBeInTheDocument();
    expect(within(items[2]!).getByRole('link', { name: 'View Q2 pulse' })).toBeInTheDocument();
    expect(within(items[3]!).getByText('Run closed')).toBeInTheDocument();
    expect(within(items[3]!).getByRole('link', { name: 'View Q1 pulse' })).toBeInTheDocument();
  });

  it('says it plainly when there is nothing to answer', async () => {
    server.use(http.get(`${ORIGIN}/respond/runs`, () => HttpResponse.json([])));
    render();
    expect(await screen.findByText('Nothing to answer')).toBeInTheDocument();
  });

  it('promises anonymity up front', async () => {
    server.use(http.get(`${ORIGIN}/respond/runs`, () => HttpResponse.json([])));
    render();
    expect(screen.getByText(/never with your name/)).toBeInTheDocument();
  });
});

describe('isAnswered', () => {
  it('counts a value as given unless it is empty', () => {
    expect(isAnswered(0)).toBe(true);
    expect(isAnswered(false)).toBe(true);
    expect(isAnswered('x')).toBe(true);
    expect(isAnswered(['A'])).toBe(true);
    for (const empty of [undefined, null, '', '   ', []]) expect(isAnswered(empty)).toBe(false);
  });
});

describe('RespondPage', () => {
  const questions = [
    { id: 'Q01', kind: 'RATING', text: 'How is morale?', required: true, options: [], labels: [], min: 1, max: 5 },
    { id: 'Q02', kind: 'SINGLE_CHOICE', text: 'How often do releases slip?', required: false, options: [{ key: 'O1', label: 'Never' }, { key: 'O2', label: 'Often' }], labels: [] },
    { id: 'Q03', kind: 'MULTI_CHOICE', text: 'Which tools do you use?', required: false, options: [{ key: 'A', label: 'Editor' }, { key: 'B', label: 'Debugger' }], labels: [] },
    { id: 'Q04', kind: 'YES_NO', text: 'Any incident last month?', required: false, options: [], labels: [] },
    { id: 'Q05', kind: 'NUMERIC', text: 'How many on-call weeks?', required: false, options: [], labels: [], min: 0, max: 10 },
    { id: 'Q06', kind: 'FREE_TEXT', text: 'Anything else?', required: false, options: [], labels: [] },
  ] as RespondentRunDetail['questions'];
  const base = (over: Partial<RespondentRunDetail> = {}): RespondentRunDetail => ({
    runId: 'r-1',
    name: 'Q4 pulse',
    questionnaireName: 'Team pulse',
    dueDate: '2026-12-01',
    runStatus: 'OPEN',
    myStatus: 'NOT_STARTED',
    questions,
    answers: {},
    ...over,
  });
  let current: RespondentRunDetail = base();
  const serve = (d: RespondentRunDetail = base()) => {
    current = d;
    server.use(http.get(`${ORIGIN}/respond/runs/r-1`, () => HttpResponse.json(current)));
  };
  const render = () => renderRoutes([{ path: '/respond/:runId', element: <RespondPage /> }, { path: '/respond', element: <p>my runs</p> }], { route: '/respond/r-1', auth: { principal: respondent } });
  const answerRequired = async () => userEvent.click(await screen.findByRole('radio', { name: '2' }));

  it('shows every kind of question with a control that suits it, and the privacy promise', async () => {
    serve();
    render();
    expect(await screen.findByRole('heading', { level: 1, name: 'Q4 pulse' })).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('never with your name');
    expect(screen.getAllByRole('radio', { name: /^[1-5]$/ })).toHaveLength(5);
    expect(screen.getByRole('radio', { name: 'Never' })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Debugger' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Yes' })).toBeInTheDocument();
    expect(screen.getByRole('spinbutton', { name: 'How many on-call weeks?' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Anything else?' })).toBeInTheDocument();
    expect(screen.getByText('How is morale?').textContent).toContain('(required)');
    expect(screen.getByText('between 0 and 10')).toBeInTheDocument();
  });

  it('shows nothing about scoring', async () => {
    serve();
    const view = render();
    await screen.findByRole('heading', { level: 1, name: 'Q4 pulse' });
    for (const word of ['dimension', 'weight', 'Team Health', 'Delivery Stability', 'desirable', 'higher is better']) expect(view.container.textContent).not.toContain(word);
  });

  it('saves a draft with every question, an empty one as nothing', async () => {
    serve();
    let body: { answers: { questionId: string; answer: unknown }[] } | undefined;
    server.use(
      http.put(`${ORIGIN}/respond/runs/r-1/draft`, async ({ request }) => {
        body = (await request.json()) as typeof body;
        current = base({ myStatus: 'IN_PROGRESS', answers: { Q01: 2, Q02: 'O2', Q03: ['A', 'B'], Q04: false } });
        return HttpResponse.json(current);
      }),
    );
    render();
    await answerRequired();
    await userEvent.click(screen.getByRole('radio', { name: 'Often' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Editor' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Debugger' }));
    await userEvent.click(screen.getByRole('radio', { name: 'No' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    expect(await screen.findByText(/Draft saved/)).toBeInTheDocument();
    expect(body?.answers).toEqual([
      { questionId: 'Q01', answer: 2 },
      { questionId: 'Q02', answer: 'O2' },
      { questionId: 'Q03', answer: ['A', 'B'] },
      { questionId: 'Q04', answer: false },
      { questionId: 'Q05', answer: null },
      { questionId: 'Q06', answer: null },
    ]);
  });

  it('continues from a draft saved earlier', async () => {
    serve(base({ myStatus: 'IN_PROGRESS', answers: { Q01: 4, Q02: 'O1', Q03: ['B'], Q04: true, Q05: 3, Q06: 'More focus time' } }));
    render();
    await screen.findByRole('heading', { level: 1, name: 'Q4 pulse' });
    expect(screen.getByRole('radio', { name: '4' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Never' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Debugger' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Editor' })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: 'Yes' })).toBeChecked();
    expect(screen.getByRole('spinbutton', { name: 'How many on-call weeks?' })).toHaveValue(3);
    expect(screen.getByRole('textbox', { name: 'Anything else?' })).toHaveValue('More focus time');
  });

  it('will not submit while a required question is open, and says which', async () => {
    serve();
    let requests = 0;
    server.use(
      http.put(`${ORIGIN}/respond/runs/r-1/draft`, () => {
        requests += 1;
        return HttpResponse.json(current);
      }),
      http.post(`${ORIGIN}/respond/runs/r-1/submit`, () => {
        requests += 1;
        return HttpResponse.json(current);
      }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Submit answers' }));
    expect(await screen.findByText('1 required question needs an answer before you can submit.')).toBeInTheDocument();
    expect(screen.getByText('This question needs an answer.')).toBeInTheDocument();
    expect(requests).toBe(0);
    await answerRequired();
    expect(screen.queryByText('This question needs an answer.')).not.toBeInTheDocument();
  });

  it('saves and then submits, and thanks the respondent without showing the answers again', async () => {
    serve();
    const calls: string[] = [];
    server.use(
      http.put(`${ORIGIN}/respond/runs/r-1/draft`, () => {
        calls.push('save');
        return HttpResponse.json(current);
      }),
      http.post(`${ORIGIN}/respond/runs/r-1/submit`, () => {
        calls.push('submit');
        current = base({ myStatus: 'SUBMITTED', answers: {} });
        return HttpResponse.json(current);
      }),
    );
    render();
    await answerRequired();
    await userEvent.click(screen.getByRole('button', { name: 'Submit answers' }));
    expect(await screen.findByText('Thank you, your answers are submitted')).toBeInTheDocument();
    expect(calls).toEqual(['save', 'submit']);
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.getByText(/stored with your team only, not with your name/)).toBeInTheDocument();
  });

  it('shows a submitted run as done, not as a form', async () => {
    serve(base({ myStatus: 'SUBMITTED' }));
    render();
    expect(await screen.findByText('Thank you, your answers are submitted')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Submit answers' })).not.toBeInTheDocument();
  });

  it('shows a closed run read only', async () => {
    serve(base({ runStatus: 'CLOSED', answers: { Q01: 3 } }));
    render();
    expect(await screen.findByText(/This run is closed/)).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '3' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Submit answers' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save draft' })).not.toBeInTheDocument();
  });

  it('shows the reason when the server refuses an answer', async () => {
    serve();
    server.use(http.put(`${ORIGIN}/respond/runs/r-1/draft`, () => HttpResponse.json({ code: 'RUN_INVALID', message: 'the answer to question Q01 must be a whole number from 1 to 5' }, { status: 422 })));
    render();
    await answerRequired();
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    expect(await screen.findByText(/must be a whole number from 1 to 5/)).toBeInTheDocument();
  });

  it('shows a run that is not yours as not found', async () => {
    server.use(http.get(`${ORIGIN}/respond/runs/r-1`, () => HttpResponse.json({ code: 'RUN_NOT_FOUND', message: 'run not found' }, { status: 404 })));
    render();
    expect(await screen.findByText('Not found')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('radio')).not.toBeInTheDocument());
  });
});
