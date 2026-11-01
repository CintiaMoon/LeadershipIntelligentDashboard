import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import * as s from '@/tests/scoped';
import { ScopeSwitcher } from './ScopeSwitcher';

const teams = s.scopedRun.teamRuns ?? [];

describe('ScopeSwitcher', () => {
  it('offers the whole organization and each team, and says which team has too few responses', () => {
    render(<ScopeSwitcher teams={teams} value={null} onChange={() => undefined} />);
    expect(screen.getByRole('combobox', { name: 'Scope' })).toHaveValue('');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Whole organization',
      'Team alpha',
      'Team small (not enough responses)',
      'Team tiny (not enough responses)',
    ]);
  });

  it('reports the chosen team, and null for the whole organization', async () => {
    const onChange = vi.fn();
    render(<ScopeSwitcher teams={teams} value="alpha" onChange={onChange} />);
    expect(screen.getByRole('combobox', { name: 'Scope' })).toHaveValue('alpha');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Scope' }), 'tiny');
    expect(onChange).toHaveBeenLastCalledWith('tiny');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Scope' }), '');
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it('is absent when the assessment has no teams', () => {
    const { container } = render(<ScopeSwitcher teams={[]} value={null} onChange={() => undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('never shows a score, so teams cannot be ranked from it', () => {
    render(<ScopeSwitcher teams={teams} value={null} onChange={() => undefined} />);
    expect(screen.queryByText(/71/)).not.toBeInTheDocument();
  });
});
