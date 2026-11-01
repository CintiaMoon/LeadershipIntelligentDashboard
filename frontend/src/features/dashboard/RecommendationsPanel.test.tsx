import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { catalog, recommendations } from '@/tests/fixtures';
import { makeNames } from './display';
import { RecommendationsPanel } from './RecommendationsPanel';

const names = makeNames(catalog);

describe('RecommendationsPanel', () => {
  it('lists the backend recommendations in the order given, with severity', () => {
    render(<RecommendationsPanel data={recommendations} names={names} />);
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0] as HTMLElement).getByRole('heading', { name: 'Review team capacity' })).toBeInTheDocument();
    expect(within(items[0] as HTMLElement).getByText('Critical')).toBeInTheDocument();
    expect(within(items[1] as HTMLElement).getByText('High')).toBeInTheDocument();
  });

  it('traces every action to the pattern behind it', () => {
    render(<RecommendationsPanel data={recommendations} names={names} />);
    expect(screen.getByRole('link', { name: 'Team burnout risk' })).toHaveAttribute('href', '#pattern-TH-001');
    expect(screen.getByRole('link', { name: 'Delivery slippage' })).toHaveAttribute('href', '#pattern-DS-001');
  });

  it('reports action keys without a description instead of inventing text', () => {
    render(<RecommendationsPanel data={recommendations} names={names} />);
    expect(screen.getByText(/1 suggested action has no description in this configuration: REVIEW_WORKLOAD_DISTRIBUTION/)).toBeInTheDocument();
  });

  it('shows an empty state when there is nothing to recommend', () => {
    render(<RecommendationsPanel data={{ recommendations: [], unmappedActionKeys: [] }} names={names} />);
    expect(screen.getByText('No recommended actions')).toBeInTheDocument();
  });
});
