import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { catalog, patternsResponse, penaltiesResponse, redactedPatternsResponse } from '@/tests/fixtures';
import { parsePatterns, parsePenalties } from '@/types/views';
import { makeNames } from './display';
import { PatternOverview } from './PatternOverview';

const names = makeNames(catalog);
const penalties = parsePenalties(penaltiesResponse.penalties);
const actionTitles = { REVIEW_TEAM_CAPACITY: 'Review team capacity', STABILIZE_DELIVERY: 'Stabilize delivery' };

function view(overrides: Partial<React.ComponentProps<typeof PatternOverview>> = {}) {
  const props = {
    patterns: parsePatterns(patternsResponse.patterns),
    penalties,
    names,
    actionTitles,
    redacted: false,
    groupBy: 'dimension' as const,
    onGroupBy: vi.fn(),
    severity: null,
    onClearSeverity: vi.fn(),
    ...overrides,
  };
  return { props, ...render(<PatternOverview {...props} />) };
}

describe('PatternOverview', () => {
  it('shows each detected pattern with name, description, severity, confidence, evidence count, dimensions and actions', () => {
    view();
    const card = screen.getByRole('article', { name: 'Team burnout risk' });
    expect(within(card).getByText('Sustained workload pressure with weak sustainability.')).toBeInTheDocument();
    expect(within(card).getByText('Critical')).toBeInTheDocument();
    expect(within(card).getByText(/Confidence:/)).toHaveTextContent('Strong');
    expect(within(card).getByText('2 evidence items')).toBeInTheDocument();
    expect(within(card).getByText('Affects: Team Health')).toBeInTheDocument();
    expect(within(card).getByText('Review team capacity')).toBeInTheDocument();
    // an action the catalog has no title for is shown by its key, never invented
    expect(within(card).getByText('Review workload distribution')).toBeInTheDocument();
  });

  it('keeps severity and confidence separate on the same card', () => {
    view();
    const card = screen.getByRole('article', { name: 'Delivery slippage' });
    expect(within(card).getByText('High')).toBeInTheDocument();
    expect(within(card).getByText(/Confidence:/)).toHaveTextContent('Moderate');
  });

  it('groups by dimension with cross-dimensional patterns in their own group', () => {
    view();
    const groups = screen.getAllByRole('group').map((g) => g.getAttribute('aria-label')).filter(Boolean);
    expect(groups).toEqual(['Team Health', 'Delivery Stability', 'Cross dimensional']);
  });

  it('regroups when the grouping changes', async () => {
    const { props } = view();
    await userEvent.click(screen.getByLabelText('Severity'));
    expect(props.onGroupBy).toHaveBeenCalledWith('severity');
  });

  it('opens the evidence behind a pattern on demand', async () => {
    view();
    const card = screen.getByRole('article', { name: 'Team burnout risk' });
    expect(within(card).queryByText('Workload pressure')).not.toBeInTheDocument();
    await userEvent.click(within(card).getByRole('button', { name: 'Show evidence' }));
    expect(within(card).getByText('Workload pressure')).toBeInTheDocument();
    expect(within(card).getByText('80.00')).toBeInTheDocument();
    expect(within(card).getByText('From: Workload level')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Hide evidence' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('lists evidence items but hides their values from roles that may not see them', async () => {
    view({ patterns: parsePatterns(redactedPatternsResponse.patterns), redacted: true });
    await userEvent.click(screen.getByRole('button', { name: 'Show evidence' }));
    expect(screen.getByText('Workload pressure')).toBeInTheDocument();
    expect(screen.getByText(/Values are hidden for your role/)).toBeInTheDocument();
    expect(screen.queryByText('80.00')).not.toBeInTheDocument();
    expect(screen.getAllByText('Hidden')).toHaveLength(2);
  });

  it('filters by severity and offers to clear the filter', async () => {
    const { props } = view({ severity: 'HIGH' });
    expect(screen.getByText(/Showing High severity only/)).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Delivery slippage' })).toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Team burnout risk' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear filter' }));
    expect(props.onClearSeverity).toHaveBeenCalled();
  });

  it('keeps patterns without enough evidence apart from findings', () => {
    view();
    const details = screen.getByText(/Conditions met but not enough evidence \(1\)/).closest('details') as HTMLElement;
    expect(within(details).getByText('Release risk')).toBeInTheDocument();
    expect(within(details).getByText('Not enough evidence')).toBeInTheDocument();
    expect(details).toHaveTextContent('never lead to a penalty');
  });

  it('shows an empty state when nothing was detected, counting what was evaluated', () => {
    view({ patterns: parsePatterns(patternsResponse.patterns.filter((p) => p.status === 'NOT_MATCHED')) });
    expect(screen.getByText('No organizational patterns detected')).toBeInTheDocument();
    expect(screen.getByText('0 detected of 1 evaluated')).toBeInTheDocument();
  });

  it('falls back to a readable code when the catalog has no name', () => {
    view({ names: makeNames(undefined) });
    expect(screen.getByRole('article', { name: 'Team burnout risk' })).toBeInTheDocument();
  });
});
