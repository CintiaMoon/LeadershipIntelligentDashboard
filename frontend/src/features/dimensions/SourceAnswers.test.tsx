import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import * as s from '@/tests/scoped';
import { SourceAnswers } from './SourceAnswers';

const [group, suppressed, team] = s.memberAnswersResponse.answers;

describe('SourceAnswers', () => {
  it('shows member answers as a count and sorted values, not in the order they were given', () => {
    render(<SourceAnswers views={[group!]} />);
    expect(screen.getByText(/5 responses, in sorted order and not in the order given/)).toBeInTheDocument();
    expect(screen.getByText('1, 2, 3, 4, 5')).toBeInTheDocument();
  });

  it('shows only the count for a group that is too small, and never its values', () => {
    render(<SourceAnswers views={[suppressed!]} />);
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText(/too few to show/)).toHaveTextContent('Groups this small are never shown');
  });

  it('labels a team figure with its team and a declared count as unverified', () => {
    render(<SourceAnswers views={[team!]} />);
    expect(screen.getByText(/Team alpha:/)).toBeInTheDocument();
    expect(screen.getByText('Yes')).toBeInTheDocument();
    expect(screen.getByText(/a figure for 8 people, as stated by the sender and not verified/)).toBeInTheDocument();
  });

  it('keeps the earlier wording for one organization answer', () => {
    render(<SourceAnswers views={[{ questionId: 'Q02', answer: 'OFTEN', scope: 'ORGANIZATION' }]} />);
    expect(screen.getByText('Answer given:')).toBeInTheDocument();
    expect(screen.getByText('OFTEN')).toBeInTheDocument();
    expect(screen.getByText(/recorded in the access log/)).toBeInTheDocument();
  });

  it('says there was no answer when the run has none for the question', () => {
    render(<SourceAnswers views={[]} />);
    expect(screen.getByText('No answer')).toBeInTheDocument();
  });

  it('shows the options chosen for a question where several may be chosen', () => {
    render(<SourceAnswers views={[{ questionId: 'Q6', answer: ['MONITORING', 'ON_CALL'], scope: 'ORGANIZATION' }]} />);
    expect(screen.getByText('MONITORING + ON_CALL')).toBeInTheDocument();
  });

  it('shows member choices as groups of lists in sorted order', () => {
    render(<SourceAnswers views={[{ questionId: 'Q6', scope: 'MEMBER', count: 2, values: [['A'], ['A', 'B']], suppressed: false }]} />);
    expect(screen.getByText('A, A + B')).toBeInTheDocument();
  });

  it('says so when nothing was chosen', () => {
    render(<SourceAnswers views={[{ questionId: 'Q6', answer: [], scope: 'ORGANIZATION' }]} />);
    expect(screen.getByText('No option chosen')).toBeInTheDocument();
  });
});
