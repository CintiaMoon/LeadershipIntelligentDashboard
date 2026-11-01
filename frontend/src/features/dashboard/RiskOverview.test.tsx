import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { RiskOverview } from './RiskOverview';

const counts = { CRITICAL: 1, HIGH: 2, MEDIUM: 0, LOW: 0 };

describe('RiskOverview', () => {
  it('shows the four severities with their counts in order', () => {
    render(<RiskOverview counts={counts} active={null} onToggle={() => undefined} />);
    expect(screen.getAllByRole('button').map((b) => b.textContent)).toEqual([
      expect.stringContaining('Critical'),
      expect.stringContaining('High'),
      expect.stringContaining('Medium'),
      expect.stringContaining('Low'),
    ]);
    expect(screen.getByRole('button', { name: /High/ })).toHaveTextContent('2');
    expect(screen.getByRole('button', { name: /Critical/ })).toHaveTextContent(/1\s*pattern detected/);
  });

  it('toggles the filter and reports which one is active', async () => {
    const onToggle = vi.fn();
    render(<RiskOverview counts={counts} active="HIGH" onToggle={onToggle} />);
    expect(screen.getByRole('button', { name: /High/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /Critical/ })).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(screen.getByRole('button', { name: /Critical/ }));
    expect(onToggle).toHaveBeenCalledWith('CRITICAL');
  });

  it('cannot filter by a severity with nothing in it', () => {
    render(<RiskOverview counts={counts} active={null} onToggle={() => undefined} />);
    expect(screen.getByRole('button', { name: /Medium/ })).toBeDisabled();
  });
});
