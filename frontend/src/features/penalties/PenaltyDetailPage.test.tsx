import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { installMockApi } from '@/tests/pages';
import { renderRoutes } from '@/tests/utils';
import { PenaltyDetailPage } from './PenaltyDetailPage';

installMockApi();
const routes = [{ path: '/runs/:runId/penalties/:penaltyId', element: <PenaltyDetailPage /> }];
const route = (id: string) => `/runs/${f.RUN_ID}/penalties/${id}`;

describe('PenaltyDetailPage', () => {
  it('shows an applied deduction as something that changed the score, with candidate and effective side by side', async () => {
    renderRoutes(routes, { route: route('TH-P01') });
    expect(await screen.findByRole('heading', { name: 'Burnout risk deduction', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'This deduction changed the score' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Requested deduction 12.00' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Effective deduction 12.00' })).toBeInTheDocument();
    expect(screen.getByText('Team Health is lowered because burnout risk was detected.')).toBeInTheDocument();
  });

  it('makes a suppressed penalty visibly a candidate: nothing effective, reason and winner shown', async () => {
    renderRoutes(routes, { route: route('DS-P01') });
    expect(await screen.findByRole('heading', { name: 'This is a candidate, not an applied deduction' })).toBeInTheDocument();
    expect(screen.getByText(/did not change any score/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Requested deduction 8.00' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Effective deduction 0.00' })).toBeInTheDocument();
    expect(screen.getByText(/Suppressed amount 8\.00/)).toBeInTheDocument();
    expect(screen.getByText(/Winning penalty/).parentElement).toHaveTextContent('Stronger delivery deduction');
  });

  it('shows target, calculation, aggregation policy, category and the triggering pattern', async () => {
    renderRoutes(routes, { route: route('TH-P01') });
    await screen.findByRole('heading', { name: 'How it was decided' });
    expect(screen.getByRole('link', { name: 'Team burnout risk' })).toHaveAttribute('href', `/runs/${f.RUN_ID}/patterns/TH-001`);
    expect(screen.getByText('Target').nextElementSibling).toHaveTextContent('Team Health');
    expect(screen.getByText('Calculation').nextElementSibling).toHaveTextContent('Severity based');
    expect(screen.getByText('Aggregation policy').nextElementSibling).toHaveTextContent('Max per category');
  });

  it('lists every penalty on the same target and the aggregated effective deduction', async () => {
    renderRoutes(routes, { route: route('TH-P01') });
    const section = (await screen.findByRole('heading', { name: /Aggregation result/ })).closest('section') as HTMLElement;
    expect(within(section).getByRole('table', { name: /Penalties targeting Team Health/ })).toBeInTheDocument();
    expect(section).toHaveTextContent('Effective deduction for Team Health after aggregation: 12.00');
  });

  it('is honest that the configured maximum is not in the stored trace', async () => {
    renderRoutes(routes, { route: route('TH-P01') });
    expect(await screen.findByText(/configured maximum deduction is not part of the stored calculation trace/)).toBeInTheDocument();
  });

  it('shows the overall score as the target of an overall penalty', async () => {
    renderRoutes(routes, { route: route('XD-P01') });
    expect(await screen.findByText('Overall score')).toBeInTheDocument();
  });

  it('reports an unknown penalty', async () => {
    renderRoutes(routes, { route: route('NOPE') });
    expect(await screen.findByText('Penalty not found')).toBeInTheDocument();
  });
});
