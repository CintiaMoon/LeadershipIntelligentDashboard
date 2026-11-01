import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import { NewQuestionnairePage } from './NewQuestionnairePage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined }));

const routes = [
  { path: '/questionnaires/new', element: <NewQuestionnairePage /> },
  { path: '/questionnaires', element: <p>library</p> },
  { path: '/questionnaires/:id', element: <p>builder</p> },
];
const render = () => renderRoutes(routes, { route: '/questionnaires/new' });

describe('NewQuestionnairePage', () => {
  it('creates an empty questionnaire and opens it in the builder', async () => {
    let body: unknown;
    server.use(
      http.post(`${ORIGIN}/questionnaires`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 'q-new', name: 'Pulse' }, { status: 201 });
      }),
    );
    const view = render();
    await userEvent.type(screen.getByLabelText('Name'), '  Pulse  ');
    await userEvent.click(screen.getByRole('button', { name: 'Create questionnaire' }));
    expect(await screen.findByText('builder')).toBeInTheDocument();
    expect(body).toEqual({ name: 'Pulse' });
    expect(view.router.state.location.pathname).toBe('/questionnaires/q-new');
  });

  it('starts from the initial questionnaire and carries the notes to the builder', async () => {
    server.use(
      http.post(`${ORIGIN}/questionnaires/from-initial`, () => HttpResponse.json({ id: 'q-copy', name: 'Ours', notes: ['imbalance rules are not part of the builder'] }, { status: 201 })),
    );
    const view = render();
    await userEvent.type(screen.getByLabelText('Name'), 'Ours');
    await userEvent.click(screen.getByLabelText(/The initial questionnaire/));
    await userEvent.click(screen.getByRole('button', { name: 'Create questionnaire' }));
    await screen.findByText('builder');
    expect(view.router.state.location.state).toEqual({ notes: ['imbalance rules are not part of the builder'] });
  });

  it('asks for a name without sending anything', async () => {
    let requests = 0;
    server.use(
      http.post(`${ORIGIN}/questionnaires`, () => {
        requests += 1;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    render();
    await userEvent.click(screen.getByRole('button', { name: 'Create questionnaire' }));
    expect(await screen.findByText('Enter a name.')).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true');
    expect(requests).toBe(0);
  });

  it('refuses a name longer than 200 characters', async () => {
    render();
    await userEvent.click(screen.getByLabelText('Name'));
    await userEvent.paste('x'.repeat(201));
    await userEvent.click(screen.getByRole('button', { name: 'Create questionnaire' }));
    expect(await screen.findByText('Use at most 200 characters.')).toBeInTheDocument();
  });

  it('shows why the server refused', async () => {
    server.use(http.post(`${ORIGIN}/questionnaires`, () => HttpResponse.json({ code: 'FORBIDDEN', message: 'no' }, { status: 403 })));
    render();
    await userEvent.type(screen.getByLabelText('Name'), 'Pulse');
    await userEvent.click(screen.getByRole('button', { name: 'Create questionnaire' }));
    expect(await screen.findByText(/You do not have access to this/)).toBeInTheDocument();
  });

  it('goes back to the library on cancel', async () => {
    render();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(await screen.findByText('library')).toBeInTheDocument();
  });
});
