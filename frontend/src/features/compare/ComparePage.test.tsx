import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import { renderRoutes } from '@/tests/utils';
import { ComparePage } from './ComparePage';

const server = installMockApi();
const routes = [{ path: '/compare', element: <ComparePage /> }];
const route = `/compare?base=${f.PREVIOUS_RUN_ID}&run=${f.RUN_ID}`;

describe('ComparePage', () => {
  it('asks for two assessments when none are chosen', async () => {
    renderRoutes(routes, { route: '/compare' });
    expect(await screen.findByText('Choose two assessments')).toBeInTheDocument();
  });

  it('shows both sides and the change when the assessments are comparable', async () => {
    renderRoutes(routes, { route });
    expect(await screen.findByText(/these are comparable/)).toHaveTextContent('+4.50');
    expect(screen.getAllByText('Earlier').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Later').length).toBeGreaterThan(0);
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('shows per-dimension change and the patterns that are new, resolved or persistent', async () => {
    renderRoutes(routes, { route });
    const table = await screen.findByRole('table', { name: 'Dimension scores in both assessments' });
    expect(within(table).getByRole('row', { name: /Team Health/ })).toHaveTextContent('+4.50');
    expect(await screen.findByText('Persistent')).toBeInTheDocument();
    expect(screen.getByText('Team burnout risk')).toBeInTheDocument();
  });

  it('explains why assessments are not comparable, shows what differs and no change at all', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/compare`, () => HttpResponse.json(f.incomparable)));
    renderRoutes(routes, { route });
    const note = await screen.findByRole('note');
    expect(note).toHaveTextContent('These assessments are not comparable');
    expect(note).toHaveTextContent('the scoring configuration changed; the assessment template version changed');
    expect(note).toHaveTextContent('No score change is shown');
    const diff = screen.getByRole('table', { name: 'Scoring basis of the two assessments' });
    expect(within(diff).getByRole('row', { name: /Scoring configuration/ })).toHaveTextContent('Differs');
    expect(within(diff).getByRole('row', { name: /Algorithm version/ })).not.toHaveTextContent('Differs');
    const dims = screen.getByRole('table', { name: 'Dimension scores in both assessments' });
    expect(within(dims).getByRole('row', { name: /Team Health/ })).toHaveTextContent('Not comparable');
    expect(screen.queryByText(/these are comparable/)).not.toBeInTheDocument();
  });

  it('validates the form: both runs are required and must differ', async () => {
    renderRoutes(routes, { route: '/compare' });
    await userEvent.click(await screen.findByRole('button', { name: 'Compare' }));
    expect(await screen.findAllByRole('alert')).toHaveLength(2);
    await userEvent.selectOptions(screen.getByLabelText(/Earlier assessment/), f.RUN_ID);
    await userEvent.selectOptions(screen.getByLabelText(/Later assessment/), f.RUN_ID);
    await userEvent.click(screen.getByRole('button', { name: 'Compare' }));
    expect(await screen.findByText('Choose two different assessments')).toBeInTheDocument();
  });

  it('compares the two assessments chosen in the form', async () => {
    const { router } = renderRoutes(routes, { route: '/compare' });
    await screen.findAllByRole('option', { name: /62\.50/ });
    await userEvent.selectOptions(screen.getByLabelText(/Earlier assessment/), f.PREVIOUS_RUN_ID);
    await userEvent.selectOptions(screen.getByLabelText(/Later assessment/), f.RUN_ID);
    await userEvent.click(screen.getByRole('button', { name: 'Compare' }));
    expect(router.state.location.search).toBe(`?base=${f.PREVIOUS_RUN_ID}&run=${f.RUN_ID}`);
  });

  it('shows an error when the comparison fails, with a reference', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/compare`, () => HttpResponse.json({ code: 'SCORING_RUN_NOT_FOUND', message: 'x', correlationId: 'c-4' }, { status: 404 })));
    renderRoutes(routes, { route });
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Not found');
    expect(alert).toHaveTextContent('c-4');
  });
});
