import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import * as s from '@/tests/scoped';
import { renderRoutes } from '@/tests/utils';
import { DimensionDetailPage } from './DimensionDetailPage';

const server = installMockApi();
const routes = [{ path: '/runs/:runId/dimensions/:dimension', element: <DimensionDetailPage /> }];

describe('DimensionDetailPage with member answers', () => {
  it('shows the answers behind a contribution as a sorted group, never one by one', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/answers`, () => HttpResponse.json(s.memberAnswersResponse)));
    renderRoutes(routes, { route: `/runs/${f.RUN_ID}/dimensions/TEAM_HEALTH` });
    const table = await screen.findByRole('table', { name: 'Contributions to Team Health' });
    await userEvent.click(within(table).getByRole('button', { name: 'Show answer' }));
    expect(await screen.findByText('1, 2, 3, 4, 5')).toBeInTheDocument();
    expect(screen.getByText(/5 responses, in sorted order and not in the order given/)).toBeInTheDocument();
  });

  it('shows only a count for a group that is too small to show', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/answers`, () => HttpResponse.json(s.memberAnswersResponse)));
    renderRoutes(routes, { route: `/runs/${f.RUN_ID}/dimensions/DELIVERY_STABILITY` });
    const table = await screen.findByRole('table', { name: 'Contributions to Delivery Stability' });
    await userEvent.click(within(table).getByRole('button', { name: 'Show answer' }));
    expect(await screen.findByText(/too few to show/)).toHaveTextContent('Groups this small are never shown');
  });
});
