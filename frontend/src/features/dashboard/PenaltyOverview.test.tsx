import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { catalog, penaltiesResponse } from '@/tests/fixtures';
import { parseEffectiveByTarget, parsePenalties } from '@/types/views';
import { makeNames } from './display';
import { PenaltyOverview } from './PenaltyOverview';

const names = makeNames(catalog);
const penalties = parsePenalties(penaltiesResponse.penalties);
const effectiveByTarget = parseEffectiveByTarget(penaltiesResponse.effectiveByTarget);

describe('PenaltyOverview', () => {
  it('counts the four states and states the effective deductions as aggregated by the backend', () => {
    render(<PenaltyOverview penalties={penalties} effectiveByTarget={effectiveByTarget} names={names} />);
    const tiles = screen.getByText('Applied', { selector: 'dt' }).closest('dl') as HTMLElement;
    for (const [state, count] of [['Applied', '1'], ['Suppressed', '1'], ['Ineligible', '1'], ['Not triggered', '1']]) {
      expect(within(tiles).getByText(state).nextElementSibling).toHaveTextContent(count as string);
    }
    expect(screen.getByText(/Effective deductions:/)).toHaveTextContent('Team Health 12.00');
  });

  it('shows an applied penalty with target, requested and effective deduction, policy, reason and trigger', () => {
    render(<PenaltyOverview penalties={penalties} effectiveByTarget={effectiveByTarget} names={names} />);
    const card = screen.getByRole('article', { name: 'Burnout risk deduction' });
    expect(within(card).getByText('Applied')).toBeInTheDocument();
    expect(within(card).getByText('Team Health')).toBeInTheDocument();
    expect(within(card).getByText('Requested deduction').nextElementSibling).toHaveTextContent('12.00');
    expect(within(card).getByText('Effective deduction').nextElementSibling).toHaveTextContent('12.00');
    expect(within(card).getByText(/Applied in full/)).toBeInTheDocument();
    expect(within(card).getByText(/aggregation policy Max per category/)).toBeInTheDocument();
    expect(within(card).getByRole('link', { name: 'Team burnout risk' })).toHaveAttribute('href', '#pattern-TH-001');
    expect(within(card).getByText('Team Health is lowered because burnout risk was detected.')).toBeInTheDocument();
  });

  it('never hides a suppressed penalty: requested amount, nothing effective, suppressed amount, reason and winner', () => {
    render(<PenaltyOverview penalties={penalties} effectiveByTarget={effectiveByTarget} names={names} />);
    const group = screen.getByRole('group', { name: 'Suppressed' });
    expect(group).toHaveTextContent('did not change any score');
    const card = within(group).getByRole('article', { name: 'Delivery slippage deduction' });
    expect(within(card).getByText('Suppressed')).toBeInTheDocument();
    expect(within(card).getByText('Requested deduction').nextElementSibling).toHaveTextContent('8.00');
    expect(within(card).getByText('Effective deduction').nextElementSibling).toHaveTextContent('0.00');
    expect(within(card).getByText('Suppressed amount').nextElementSibling).toHaveTextContent('8.00');
    expect(card).toHaveTextContent('a larger penalty in the same category applies instead');
    expect(card).toHaveTextContent('winning penalty: Stronger delivery deduction');
  });

  it('shows ineligible penalties and keeps not-triggered ones one click away', () => {
    render(<PenaltyOverview penalties={penalties} effectiveByTarget={effectiveByTarget} names={names} />);
    expect(within(screen.getByRole('group', { name: 'Ineligible' })).getByText(/lacks enough evidence to penalize/)).toBeInTheDocument();
    const details = screen.getByText('Not triggered (1)').closest('details') as HTMLElement;
    expect(within(details).getByText('Overall score')).toBeInTheDocument();
  });

  it('only offers the four defined states', () => {
    render(<PenaltyOverview penalties={penalties} effectiveByTarget={effectiveByTarget} names={names} />);
    expect(screen.queryByText(/rejected/i)).not.toBeInTheDocument();
  });

  it('explains when no penalty rules exist', () => {
    render(<PenaltyOverview penalties={[]} effectiveByTarget={{}} names={names} />);
    expect(screen.getByText('No penalties configured')).toBeInTheDocument();
  });
});
