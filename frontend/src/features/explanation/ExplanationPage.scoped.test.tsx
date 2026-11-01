import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import * as s from '@/tests/scoped';
import { renderRoutes } from '@/tests/utils';
import type { ScoringResult } from '@/types/api';
import { ExplanationPage } from './ExplanationPage';

const server = installMockApi();
const routes = [{ path: '/runs/:runId/explanation', element: <ExplanationPage /> }];

function serve(id: string, run: ScoringResult) {
  server.use(
    http.get(`${ORIGIN}/scoring-runs/${id}`, () => HttpResponse.json(run)),
    http.get(`${ORIGIN}/scoring-runs/${id}/explanation`, () => HttpResponse.json(f.traceResponse)),
    http.get(`${ORIGIN}/scoring-runs/${id}/catalog`, () => HttpResponse.json(f.catalog)),
  );
}

describe('ExplanationPage for answers from several people', () => {
  it('says which team a run belongs to and how its answers were combined', async () => {
    serve(s.TEAM_RUN_ID, s.teamRun);
    renderRoutes(routes, { route: `/runs/${s.TEAM_RUN_ID}/explanation` });
    expect(await screen.findByText(/Team alpha/)).toHaveTextContent('Run of');
    expect(await screen.findByRole('heading', { name: 'How the answers were combined' })).toBeInTheDocument();
    const row = screen.getAllByRole('row').find((r) => r.textContent?.includes('Individual responses'));
    expect(row).toHaveTextContent('Median6Counted');
  });

  it('explains why a team below the minimum has no score', async () => {
    serve(s.SMALL_TEAM_RUN_ID, s.smallTeamRun);
    renderRoutes(routes, { route: `/runs/${s.SMALL_TEAM_RUN_ID}/explanation` });
    expect(await screen.findByRole('heading', { name: 'How the answers were combined' })).toBeInTheDocument();
    expect(screen.getByRole('note', { name: '' })).toHaveTextContent('fewer than 5 responses');
    expect(screen.getByRole('note')).toHaveTextContent('Groups this small are never shown');
  });

  it('shows the combination for the organization run too, without a team in the subtitle', async () => {
    serve(f.RUN_ID, s.scopedRun);
    renderRoutes(routes, { route: `/runs/${f.RUN_ID}/explanation` });
    expect(await screen.findByRole('heading', { name: 'How the answers were combined' })).toBeInTheDocument();
    expect(screen.queryByText(/Team alpha/)).not.toBeInTheDocument();
  });

  it('is unchanged for a run scored from one answer per question', async () => {
    renderRoutes(routes, { route: `/runs/${f.RUN_ID}/explanation` });
    expect(await screen.findByRole('heading', { name: 'How the Leadership Intelligence Score was calculated' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'How the answers were combined' })).not.toBeInTheDocument();
  });
});
