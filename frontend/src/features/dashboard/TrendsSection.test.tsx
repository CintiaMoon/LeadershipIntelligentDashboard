import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { catalog, trends } from '@/tests/fixtures';
import { makeNames } from './display';
import { TrendsSection } from './TrendsSection';

const names = makeNames(catalog);

describe('TrendsSection', () => {
  it('asks for a second assessment before drawing any trend', () => {
    render(<TrendsSection trends={{ ...trends, points: trends.points?.slice(0, 1) }} names={names} />);
    expect(screen.getByText('Trends appear after two assessments')).toBeInTheDocument();
  });

  it('summarizes the score from first to last using stored values', () => {
    render(<TrendsSection trends={trends} names={names} />);
    expect(screen.getByText('From 55.00 to 67.00 across 3 assessments.')).toBeInTheDocument();
  });

  it('flags where the scoring basis changed and says why, rather than hiding it', () => {
    render(<TrendsSection trends={trends} names={names} />);
    const note = screen.getByRole('note');
    expect(note).toHaveTextContent('These assessments were not scored the same way');
    expect(note).toHaveTextContent('the scoring configuration changed');
    expect(note).toHaveTextContent('not a like-for-like change');
  });

  it('gives every chart a data table with the exact stored values', () => {
    render(<TrendsSection trends={trends} names={names} />);
    const tables = screen.getAllByRole('table', { hidden: true });
    expect(tables.length).toBe(4);
    const lis = tables[0] as HTMLElement;
    expect(within(lis).getAllByRole('row', { hidden: true })).toHaveLength(4);
    expect(lis).toHaveTextContent('62.50');
    const penalties = tables[3] as HTMLElement;
    expect(penalties).toHaveTextContent('8.00');
  });

  it('counts how often each pattern was detected, with its configured name', () => {
    render(<TrendsSection trends={trends} names={names} />);
    expect(screen.getByText('Team burnout risk').closest('li')).toHaveTextContent('detected in 3 of 3 assessments');
    expect(screen.getByText('Delivery slippage').closest('li')).toHaveTextContent('detected in 1 of 3 assessments');
  });

  it('does not flag a change when every assessment is comparable', () => {
    const comparable = { ...trends, points: trends.points?.map((p) => ({ ...p, run: { ...p.run, comparable: true, notComparableReasons: [] } })) };
    render(<TrendsSection trends={comparable} names={names} />);
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });
});
