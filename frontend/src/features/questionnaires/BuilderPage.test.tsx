import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import type { QuestionnaireDetail, QuestionnaireDocument } from '@/types/api';
import { BuilderPage } from './BuilderPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined }));

const routes = [
  { path: '/questionnaires/:questionnaireId', element: <BuilderPage /> },
  { path: '/questionnaires', element: <p>library</p> },
];
const draftDoc: QuestionnaireDocument = {
  name: 'Team pulse',
  description: 'A short check',
  questions: [
    { id: 'Q01', kind: 'RATING', text: 'How is morale?', required: true, dimension: 'TEAM_HEALTH', options: [], labels: [] },
    { id: 'Q02', kind: 'SINGLE_CHOICE', text: 'How often do releases slip?', required: false, dimension: 'DELIVERY_STABILITY', options: [{ key: 'O1', label: 'Never' }, { key: 'O2', label: 'Often' }], labels: [] },
    { id: 'Q03', kind: 'FREE_TEXT', text: 'Anything else?', required: false, options: [], labels: [] },
  ],
  rules: [],
};
const detail = (over: Partial<QuestionnaireDetail> = {}): QuestionnaireDetail => ({
  id: 'q-1',
  name: 'Team pulse',
  state: 'DRAFT',
  archived: false,
  version: 1,
  versionStatus: 'DRAFT',
  document: draftDoc,
  problems: ['dimension TECHNICAL_RISK has no scored question', 'dimension LEADERSHIP_LOAD has no scored question'],
  versions: [{ version: 1, status: 'DRAFT' }],
  notes: [],
  ...over,
});
// the fake server remembers the last version it returned, as the real one does, so a refresh after a save shows the saved version
let current: QuestionnaireDetail = detail();
const remember = (d: QuestionnaireDetail) => {
  current = d;
  return HttpResponse.json(d);
};
const serve = (d: QuestionnaireDetail = detail()) => {
  current = d;
  server.use(http.get(`${ORIGIN}/questionnaires/q-1`, () => HttpResponse.json(current)));
};
const open = async (d?: QuestionnaireDetail) => {
  serve(d);
  renderRoutes(routes, { route: '/questionnaires/q-1' });
  await screen.findByRole('heading', { level: 1, name: d?.name ?? 'Team pulse' });
};

describe('BuilderPage: showing a draft', () => {
  it('shows the details, the questions and what is still missing', async () => {
    await open();
    expect(screen.getByLabelText('Name')).toHaveValue('Team pulse');
    expect(screen.getByLabelText('Description')).toHaveValue('A short check');
    expect(screen.getByRole('heading', { name: 'Questions (3)' })).toBeInTheDocument();
    expect(screen.getByLabelText('Question 1 text')).toHaveValue('How is morale?');
    expect(screen.getByLabelText('Question 1 type')).toHaveValue('RATING');
    expect(screen.getByLabelText('Question 1 dimension')).toHaveValue('TEAM_HEALTH');
    expect(screen.getByLabelText('Option 1 label')).toHaveValue('Never');
    expect(screen.getByText('Collected, never scored')).toBeInTheDocument();
    const problems = screen.getByRole('heading', { name: 'Before you publish' }).closest('section') as HTMLElement;
    expect(within(problems).getByText('dimension TECHNICAL_RISK has no scored question')).toBeInTheDocument();
  });

  it('summarizes the questionnaire', async () => {
    await open();
    const summary = screen.getByRole('complementary', { name: 'Summary' });
    expect(within(summary).getByText('Questions').nextSibling).toHaveTextContent('3');
    expect(within(summary).getByText('Required').nextSibling).toHaveTextContent('1');
    expect(within(summary).getByText('Dimensions covered').nextSibling).toHaveTextContent('2 of 4');
    expect(within(summary).getByText('Anonymous').nextSibling).toHaveTextContent('Yes');
  });

  it('says nothing is missing when the server reports no problems', async () => {
    await open(detail({ problems: [] }));
    expect(screen.getByText('Nothing is missing.')).toBeInTheDocument();
  });
});

describe('BuilderPage: editing', () => {
  it('adds a question of the chosen kind with its starting settings', async () => {
    await open();
    await userEvent.click(within(screen.getByRole('group', { name: 'Add a question' })).getByRole('button', { name: /Yes \/ No/ }));
    expect(screen.getByRole('heading', { name: 'Questions (4)' })).toBeInTheDocument();
    expect(screen.getByLabelText('Question 4 type')).toHaveValue('YES_NO');
    expect(screen.getByLabelText('The desirable answer is')).toHaveValue('YES');
  });

  it('adds and removes options of a choice question, keeping at least two', async () => {
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Add option' }));
    expect(screen.getAllByLabelText(/Option \d label/)).toHaveLength(3);
    await userEvent.click(screen.getByRole('button', { name: 'Remove option 3 of Question 2' }));
    expect(screen.getAllByLabelText(/Option \d label/)).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Remove option 1 of Question 2' })).toBeDisabled();
  });

  it('changes the kind of a question and shows that kind settings', async () => {
    await open();
    await userEvent.selectOptions(screen.getByLabelText('Question 1 type'), 'NUMERIC');
    expect(screen.getByLabelText('Minimum')).toHaveValue(0);
    expect(screen.getByLabelText('Maximum')).toHaveValue(10);
    await userEvent.selectOptions(screen.getByLabelText('Question 1 type'), 'FREE_TEXT');
    expect(screen.queryByLabelText('Question 1 dimension')).not.toBeInTheDocument();
  });

  it('moves and removes questions', async () => {
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Move Question 2 up' }));
    expect(screen.getByLabelText('Question 1 text')).toHaveValue('How often do releases slip?');
    expect(screen.getByRole('button', { name: 'Move Question 1 up' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Remove Question 1' }));
    expect(screen.getByRole('heading', { name: 'Questions (2)' })).toBeInTheDocument();
  });

  it('adds a rule with its condition, and a deduction and an action on request', async () => {
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Add rule' }));
    expect(screen.getByRole('heading', { name: 'Rules (1)' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Rule 1 name'), 'Low morale');
    expect(screen.getByLabelText('What is compared')).toHaveValue('question:Q01');
    await userEvent.click(screen.getByLabelText('Detecting it lowers the score'));
    expect(screen.getByLabelText('Lowers')).toHaveValue('TEAM_HEALTH');
    await userEvent.selectOptions(screen.getByLabelText('Several deductions'), 'CAPPED_STACK');
    expect(screen.getByLabelText('Cap')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Recommend an action'));
    expect(screen.getByLabelText('Recommendation title')).toBeInTheDocument();
  });

  it('enables saving only after a change', async () => {
    await open();
    expect(screen.getByRole('button', { name: 'Save draft' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Name'), '!');
    expect(screen.getByRole('button', { name: 'Save draft' })).toBeEnabled();
    expect(screen.getByText(/You have unsaved changes/)).toBeInTheDocument();
  });
});

describe('BuilderPage: saving and publishing', () => {
  it('saves the document and shows the refreshed checks', async () => {
    let sent: QuestionnaireDocument | undefined;
    await open();
    server.use(
      http.put(`${ORIGIN}/questionnaires/q-1`, async ({ request }) => {
        sent = (await request.json()) as QuestionnaireDocument;
        return remember(detail({ name: sent.name, document: sent, problems: ['dimension LEADERSHIP_LOAD has no scored question'] }));
      }),
    );
    await userEvent.type(screen.getByLabelText('Name'), ' 2');
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(sent?.name).toBe('Team pulse 2'));
    expect(sent?.questions).toHaveLength(3);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save draft' })).toBeDisabled());
    expect(await screen.findByText('dimension LEADERSHIP_LOAD has no scored question')).toBeInTheDocument();
    expect(screen.queryByText('dimension TECHNICAL_RISK has no scored question')).not.toBeInTheDocument();
  });

  it('saves unsaved changes first and then publishes', async () => {
    const calls: string[] = [];
    await open(detail({ problems: [] }));
    server.use(
      http.put(`${ORIGIN}/questionnaires/q-1`, async ({ request }) => {
        calls.push('save');
        return remember(detail({ document: (await request.json()) as QuestionnaireDocument, problems: [] }));
      }),
      http.post(`${ORIGIN}/questionnaires/q-1/publish`, () => {
        calls.push('publish');
        return remember(detail({ state: 'PUBLISHED', versionStatus: 'PUBLISHED', problems: [] }));
      }),
    );
    await userEvent.type(screen.getByLabelText('Name'), '!');
    await userEvent.click(screen.getByRole('button', { name: 'Publish' }));
    await waitFor(() => expect(calls).toEqual(['save', 'publish']));
    expect(await screen.findByText(/This version is published and never changes/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument();
  });

  it('publishes without saving when nothing changed', async () => {
    const calls: string[] = [];
    await open(detail({ problems: [] }));
    server.use(
      http.post(`${ORIGIN}/questionnaires/q-1/publish`, () => {
        calls.push('publish');
        return remember(detail({ state: 'PUBLISHED', versionStatus: 'PUBLISHED', problems: [] }));
      }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Publish' }));
    await waitFor(() => expect(calls).toEqual(['publish']));
  });

  it('lists every problem when the server refuses to publish', async () => {
    await open();
    server.use(
      http.post(`${ORIGIN}/questionnaires/q-1/publish`, () =>
        HttpResponse.json(
          {
            code: 'QUESTIONNAIRE_INVALID',
            message: 'dimension TECHNICAL_RISK has no scored question (and 1 more)',
            details: { problem01: 'dimension TECHNICAL_RISK has no scored question', problem02: 'rule X-1 refers to an unknown question Q09' },
          },
          { status: 422 },
        ),
      ),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Publish' }));
    const alert = await screen.findByText('rule X-1 refers to an unknown question Q09');
    expect(alert.closest('[role="alert"]')).toHaveTextContent('dimension TECHNICAL_RISK has no scored question');
  });
});

describe('BuilderPage: other states', () => {
  it('shows a published version read only and offers to edit it as a new draft', async () => {
    await open(detail({ state: 'PUBLISHED', versionStatus: 'PUBLISHED', problems: [] }));
    expect(screen.getByLabelText('Name')).toHaveAttribute('readonly');
    expect(screen.queryByRole('group', { name: 'Add a question' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Before you publish' })).not.toBeInTheDocument();
    let sent = false;
    server.use(
      http.put(`${ORIGIN}/questionnaires/q-1`, () => {
        sent = true;
        return remember(detail({ version: 2, state: 'PUBLISHED_WITH_DRAFT' }));
      }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Edit as a new draft' }));
    await waitFor(() => expect(sent).toBe(true));
    expect(await screen.findByRole('button', { name: 'Publish' })).toBeInTheDocument();
  });

  it('shows an archived questionnaire read only with no way to change it', async () => {
    await open(detail({ state: 'ARCHIVED', archived: true, versionStatus: 'PUBLISHED', problems: [] }));
    expect(screen.getByText(/This questionnaire is archived/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit as a new draft' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument();
  });

  it('shows a questionnaire that cannot be read as an error with a retry', async () => {
    server.use(http.get(`${ORIGIN}/questionnaires/q-1`, () => HttpResponse.json({ code: 'QUESTIONNAIRE_NOT_FOUND', message: 'questionnaire not found' }, { status: 404 })));
    renderRoutes(routes, { route: '/questionnaires/q-1' });
    expect(await screen.findByText('Not found')).toBeInTheDocument();
  });

  it('keeps the notes from the copy of the initial questionnaire in view', async () => {
    serve();
    const view = renderRoutes(routes, { route: '/questionnaires/q-1' });
    await screen.findByRole('heading', { level: 1, name: 'Team pulse' });
    await view.router.navigate('/questionnaires/q-1', { state: { notes: ['pattern TH-002 uses a condition the builder cannot express'] } });
    expect(await screen.findByText('pattern TH-002 uses a condition the builder cannot express')).toBeInTheDocument();
  });
});

