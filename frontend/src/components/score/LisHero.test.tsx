import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { lisBands, run, summary } from '@/tests/fixtures';
import { LisHero } from './LisHero';

const renderHero = (ui: React.ReactElement) => render(<MemoryRouter>{ui}</MemoryRouter>);

const base = { result: run, bands: lisBands, history: [55, 62.5, 67], templateId: 'tpl-1' };

describe('LisHero', () => {
  it('shows the backend score, label, band range, date and the diagnostic notice', () => {
    renderHero(<LisHero {...base} summary={summary} />);
    expect(screen.getByTestId('lis-value')).toHaveTextContent('67.00');
    expect(screen.getAllByText('Healthy').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Band 60.80/)).toBeInTheDocument();
    expect(screen.getByText(/Last assessment/)).toHaveTextContent('2026');
    expect(screen.getByText(/not an individual performance rating/)).toBeInTheDocument();
  });

  it('places the score on the configured bands with a text alternative', () => {
    renderHero(<LisHero {...base} summary={summary} />);
    expect(screen.getByRole('img', { name: /Score 67\.00, in the Healthy band \(60 to 80\)/ })).toBeInTheDocument();
  });

  it('shows the change only when the runs are comparable', () => {
    renderHero(<LisHero {...base} summary={summary} />);
    expect(screen.getByText('+4.50')).toBeInTheDocument();
    expect(screen.getByText(/previous assessment \(62\.50\)/)).toBeInTheDocument();
  });

  it('names the reasons instead of showing a change when runs are not comparable', () => {
    renderHero(<LisHero {...base} summary={{ ...summary, comparable: false, change: undefined, notComparableReasons: ['CONFIGURATION_DIFFERS', 'TEMPLATE_VERSION_DIFFERS'] }} />);
    const note = screen.getByRole('note');
    expect(note).toHaveTextContent('Not comparable with the previous assessment (62.50)');
    expect(note).toHaveTextContent('the scoring configuration changed; the assessment template version changed');
    expect(screen.queryByText('+4.50')).not.toBeInTheDocument();
  });

  it('says so when there is no earlier assessment', () => {
    renderHero(<LisHero {...base} summary={{ ...summary, previousScoringRunId: undefined, previousScore: undefined, change: undefined, comparable: false }} history={[67]} />);
    expect(screen.getByText(/First assessment in this series/)).toBeInTheDocument();
    expect(screen.getByText('Trend appears after two assessments.')).toBeInTheDocument();
  });

  it('gives the trend a text alternative listing the stored values', () => {
    renderHero(<LisHero {...base} summary={summary} />);
    expect(screen.getByText(/oldest to newest: 55\.00, 62\.50, 67\.00/, { selector: 'p' })).toBeInTheDocument();
  });

  it('never invents a score when the backend has none', () => {
    renderHero(<LisHero {...base} result={{ ...run, leadershipIntelligenceScore: undefined, interpretation: undefined }} summary={summary} />);
    const value = screen.getByTestId('lis-value');
    expect(within(value.parentElement as HTMLElement).getByText('\u2013')).toBeInTheDocument();
    expect(value).not.toHaveTextContent('0.00');
  });
});
