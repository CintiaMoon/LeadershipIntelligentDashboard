import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Waterfall } from './Waterfall';

const dimensions = [
  { dimension: 'A', weightedContribution: 3.25 },
  { dimension: 'B', weightedContribution: 18.75 },
  { dimension: 'C', weightedContribution: 25 },
];
const names = (d: string) => `Dimension ${d}`;

describe('Waterfall', () => {
  it('lists each dimension, the weighted score, the overall penalty and the final score exactly as given', () => {
    render(<Waterfall dimensions={dimensions} names={names} weightedAdjustedScore={47} overallPenalty={2} finalScore={45} />);
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(6);
    expect(within(items[0] as HTMLElement).getByText('+3.25')).toBeInTheDocument();
    expect(within(items[2] as HTMLElement).getByText('+25.00')).toBeInTheDocument();
    expect(within(items[3] as HTMLElement).getByText('47.00')).toBeInTheDocument();
    expect(within(items[4] as HTMLElement).getByText(/2\.00/)).toBeInTheDocument();
    expect(within(items[5] as HTMLElement).getByText('45.00')).toBeInTheDocument();
  });

  it('draws each bar from its own value and lines it up after earlier ones with invisible spacers', () => {
    render(<Waterfall dimensions={dimensions} names={names} weightedAdjustedScore={47} overallPenalty={2} finalScore={45} />);
    const third = screen.getByRole('img', { name: 'Dimension C adds 25.00' });
    expect(Array.from(third.children).map((c) => (c as HTMLElement).style.width)).toEqual(['3.25%', '18.75%', '25%']);
    const penalty = screen.getByRole('img', { name: 'Overall penalty 2.00' });
    expect(Array.from(penalty.children).map((c) => (c as HTMLElement).style.width)).toEqual(['45%', '2%']);
  });

  it('has a text alternative that states every figure', () => {
    render(<Waterfall dimensions={dimensions} names={names} weightedAdjustedScore={47} overallPenalty={2} finalScore={45} />);
    const caption = document.querySelector('figcaption') as HTMLElement;
    expect(caption).toHaveTextContent('Dimension A adds 3.25');
    expect(caption).toHaveTextContent('weighted adjusted score 47.00; overall penalty 2.00; final score 45.00');
  });

  it('skips a dimension that has no contribution instead of showing zero', () => {
    render(<Waterfall dimensions={[...dimensions, { dimension: 'D', weightedContribution: undefined }]} names={names} weightedAdjustedScore={47} overallPenalty={0} finalScore={47} />);
    expect(screen.queryByText('Dimension D')).not.toBeInTheDocument();
  });
});
