import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeNames } from '@/features/dashboard/display';
import * as f from '@/tests/fixtures';
import * as s from '@/tests/scoped';
import { parseResponses } from '@/types/views';
import { ResponsesPanel } from './ResponsesPanel';

const names = makeNames(f.catalog);
const parsed = (value: unknown) => {
  const responses = parseResponses(value);
  if (!responses) throw new Error('expected response summaries');
  return responses;
};

describe('ResponsesPanel', () => {
  it('shows how each question was combined, with the number of responses and where the count comes from', () => {
    render(<ResponsesPanel responses={parsed(s.responses)} names={names} />);
    const table = screen.getByRole('table', { name: 'How the answers of each question were combined' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    expect(rows[1]).toHaveTextContent('Individual responses');
    expect(rows[1]).toHaveTextContent('Median');
    expect(rows[1]).toHaveTextContent('11');
    expect(rows[1]).toHaveTextContent('Counted');
    expect(screen.getByText('A question needs at least 5 responses to be used.')).toBeInTheDocument();
  });

  it('says what a declared or unknown count is, so the reader does not mistake it for a measured one', () => {
    const value = {
      questions: [
        { ...s.responses.questions[0], scope: 'TEAM', participation: 'DECLARED', respondents: 12 },
        { ...s.responses.questions[1], scope: 'ORGANIZATION', participation: 'UNKNOWN', respondents: null, method: 'MEAN' },
      ],
    };
    render(<ResponsesPanel responses={parsed(value)} names={names} />);
    expect(screen.getByText('Stated by the sender, not verified')).toBeInTheDocument();
    expect(screen.getAllByText('Not known')).toHaveLength(2);
    expect(screen.getByText('Mean')).toBeInTheDocument();
    expect(screen.getByText('Team figures')).toBeInTheDocument();
    expect(screen.getByText('Organization figure')).toBeInTheDocument();
  });

  it('explains questions that had too few responses and that small groups are never shown', () => {
    render(<ResponsesPanel responses={parsed(s.smallTeamRun.responses)} names={names} insufficient />);
    const note = screen.getByRole('note');
    expect(note).toHaveTextContent('2 questions had fewer than 5 responses, so they were not used.');
    expect(note).toHaveTextContent('Groups this small are never shown');
    expect(note).toHaveTextContent('no score is shown for this scope');
    expect(screen.getAllByText('Too few responses')).toHaveLength(2);
  });

  it('says so when a scope has no score although no question was short', () => {
    render(<ResponsesPanel responses={parsed(s.responses)} names={names} insufficient />);
    expect(screen.getByRole('note')).toHaveTextContent('No score is shown for this scope because too little data was available.');
  });

  it('shows no alert when every question was used', () => {
    render(<ResponsesPanel responses={parsed(s.responses)} names={names} />);
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('renders nothing without questions', () => {
    const { container } = render(<ResponsesPanel responses={parsed({ questions: [] })} names={names} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('never shows an answer value: only counts, the method and the minimum', () => {
    const { container } = render(<ResponsesPanel responses={parsed(s.responses)} names={names} />);
    expect(container.textContent).not.toMatch(/OFTEN/);
  });
});

describe('parseResponses', () => {
  it('is null for a run scored from one answer per question', () => {
    expect(parseResponses(undefined)).toBeNull();
    expect(parseResponses(null)).toBeNull();
  });

  it('keeps the teams that could not be scored and defaults the lists', () => {
    expect(parseResponses({ unscoredTeams: ['t9'] })).toEqual({ questions: [], unscoredTeams: ['t9'] });
  });

  it('turns an unexpected shape into a clear error', () => {
    expect(() => parseResponses({ questions: [{ questionId: 1 }] })).toThrow('response summaries');
  });
});
