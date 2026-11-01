import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import { QuestionnaireListPage } from './QuestionnaireListPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined }));

const items = [
  { id: 'q-1', name: 'Q3 Leadership 360', state: 'PUBLISHED', archived: false, publishedVersion: 3, updatedAt: '2026-10-30T09:00:00Z' },
  { id: 'q-2', name: 'Delivery Retro', state: 'DRAFT', archived: false, draftVersion: 1, updatedAt: '2026-10-31T09:00:00Z' },
  { id: 'q-3', name: 'Team Pulse', state: 'PUBLISHED_WITH_DRAFT', archived: false, publishedVersion: 1, draftVersion: 2, updatedAt: '2026-10-29T09:00:00Z' },
  { id: 'q-4', name: 'Legacy Survey', state: 'ARCHIVED', archived: true, publishedVersion: 1, updatedAt: '2026-01-02T09:00:00Z' },
];
const routes = [
  { path: '/questionnaires', element: <QuestionnaireListPage /> },
  { path: '/questionnaires/new', element: <p>new page</p> },
  { path: '/questionnaires/:id', element: <p>builder page</p> },
];
const render = () => renderRoutes(routes, { route: '/questionnaires' });
const serve = (list: Record<string, unknown>[] = items) => server.use(http.get(`${ORIGIN}/questionnaires`, () => HttpResponse.json(list)));

describe('QuestionnaireListPage', () => {
  it('lists every questionnaire with its state written out and its versions', async () => {
    serve();
    render();
    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(4);
    expect(within(rows[0]!).getByText('Published')).toBeInTheDocument();
    expect(within(rows[0]!).getByText('v3 published')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('Draft')).toBeInTheDocument();
    expect(within(rows[2]!).getByText('Published, newer draft')).toBeInTheDocument();
    expect(within(rows[2]!).getByText(/v1 published, v2 draft/)).toBeInTheDocument();
    expect(within(rows[3]!).getByText('Archived')).toBeInTheDocument();
    expect(within(rows[3]!).getByRole('link', { name: 'View' })).toBeInTheDocument();
    expect(within(rows[3]!).queryByRole('button', { name: /Archive/ })).not.toBeInTheDocument();
  });

  it('filters by state and by name', async () => {
    serve();
    render();
    await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: 'Drafts' }));
    expect(screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('rowheader')[0]?.textContent)).toEqual(['Delivery Retro', 'Team Pulse']);
    await userEvent.click(screen.getByRole('button', { name: 'Archived' }));
    expect(screen.getAllByRole('row').slice(1)).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'All' }));
    await userEvent.type(screen.getByLabelText('Search'), 'pulse');
    expect(screen.getAllByRole('row').slice(1)).toHaveLength(1);
    await userEvent.clear(screen.getByLabelText('Search'));
    await userEvent.type(screen.getByLabelText('Search'), 'nothing like this');
    expect(screen.getByText('No questionnaire matches.')).toBeInTheDocument();
  });

  it('duplicates a questionnaire and opens the copy', async () => {
    serve();
    let called = '';
    server.use(
      http.post(`${ORIGIN}/questionnaires/:id/duplicate`, ({ params }) => {
        called = String(params.id);
        return HttpResponse.json({ id: 'q-copy', name: 'Q3 Leadership 360 (copy)' }, { status: 201 });
      }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Duplicate Q3 Leadership 360' }));
    expect(await screen.findByText('builder page')).toBeInTheDocument();
    expect(called).toBe('q-1');
  });

  it('archives a questionnaire', async () => {
    serve();
    let called = '';
    server.use(
      http.post(`${ORIGIN}/questionnaires/:id/archive`, ({ params }) => {
        called = String(params.id);
        return HttpResponse.json({ id: 'q-2', state: 'ARCHIVED' });
      }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Archive Delivery Retro' }));
    await screen.findByRole('table');
    expect(called).toBe('q-2');
  });

  it('invites the first questionnaire when there is none', async () => {
    serve([]);
    render();
    expect(await screen.findByText('No questionnaires yet')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'New questionnaire' }).length).toBeGreaterThan(0);
  });

  it('shows the error with a way to try again when the list cannot be read', async () => {
    server.use(http.get(`${ORIGIN}/questionnaires`, () => HttpResponse.json({ code: 'X', message: 'boom' }, { status: 503 })));
    render();
    expect(await screen.findByRole('alert')).toHaveTextContent('temporarily unavailable');
  });
});
