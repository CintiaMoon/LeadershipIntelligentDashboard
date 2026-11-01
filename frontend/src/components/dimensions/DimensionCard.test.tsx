import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeNames } from '@/features/dashboard/display';
import { catalog, dimensionsResponse, run } from '@/tests/fixtures';
import { parseDimensionDetails } from '@/types/views';
import { DimensionCard } from './DimensionCard';

const names = makeNames(catalog);
const details = parseDimensionDetails(dimensionsResponse.dimensions);
const noRisk = { highestSeverity: null, detected: 0, appliedPenalties: 0, suppressedPenalties: 0 };

const props = {
  dimension: names.dimension('TEAM_HEALTH'),
  result: run.dimensions?.TEAM_HEALTH,
  detail: details[0],
  names,
  history: [8.5, 13],
  risk: { highestSeverity: 'CRITICAL', detected: 1, appliedPenalties: 1, suppressedPenalties: 0 },
  redacted: false,
};

describe('DimensionCard', () => {
  it('shows the adjusted score, raw score, penalty, label, risk and penalties', () => {
    render(<DimensionCard {...props} />);
    expect(screen.getByRole('heading', { name: 'Team Health' })).toBeInTheDocument();
    expect(screen.getByText('13.00')).toBeInTheDocument();
    expect(screen.getByText(/Raw 25\.00/)).toHaveTextContent('penalty 12.00');
    // one badge for the interpretation label, one for the risk severity
    expect(screen.getAllByText('Critical')).toHaveLength(2);
    expect(screen.getByText('1 pattern detected')).toBeInTheDocument();
    expect(screen.getByText('Penalties: 1 applied, 0 suppressed')).toBeInTheDocument();
  });

  it('lists the key contributing signals by their configured names', () => {
    render(<DimensionCard {...props} />);
    expect(screen.getByText('Sustainable pace')).toBeInTheDocument();
  });

  it('shows the change since the previous comparable assessment when given', () => {
    render(<DimensionCard {...props} change={4.5} />);
    expect(screen.getByText('+4.50')).toBeInTheDocument();
  });

  it('shows no change when none is given', () => {
    render(<DimensionCard {...props} />);
    expect(screen.queryByText(/Since the previous assessment/)).not.toBeInTheDocument();
  });

  it('hides question-level detail from roles that may not see it', () => {
    render(<DimensionCard {...props} detail={undefined} redacted />);
    expect(screen.getByText('Question-level detail is hidden for your role.')).toBeInTheDocument();
  });

  it('reports "not enough data" instead of a score when a dimension is insufficient', () => {
    render(<DimensionCard {...props} result={{ status: 'INSUFFICIENT_DATA', coverage: 0.5 }} risk={noRisk} />);
    expect(screen.getByText('Not enough data')).toBeInTheDocument();
    expect(screen.getByText(/Coverage 50%/)).toBeInTheDocument();
    expect(screen.queryByText('0.00')).not.toBeInTheDocument();
  });

  it('says plainly when no risk pattern was detected', () => {
    render(<DimensionCard {...props} risk={noRisk} />);
    expect(screen.getByText('No risk patterns detected')).toBeInTheDocument();
  });
});
