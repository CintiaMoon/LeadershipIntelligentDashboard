import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import { renderRoutes } from '@/tests/utils';
import { PatternDetailPage } from './PatternDetailPage';

const server = installMockApi();
const routes = [{ path: '/runs/:runId/patterns/:patternId', element: <PatternDetailPage /> }];

/** The patterns of the run, with the confidence breakdown of confidence algorithm 2 (participation, decision D45). */
function withBreakdown(extra: Record<string, unknown>) {
  const patterns = (f.patternsResponse.patterns as Record<string, unknown>[]).map((p) =>
    p.patternId === 'TH-001'
      ? {
          ...p,
          confidenceBreakdown: { coverage: 1, independence: 1, reliability: 1, freshness: 1, supportingSignals: 2, independentGroups: 2, algorithmVersion: '2', ...extra },
        }
      : p,
  );
  return HttpResponse.json({ ...f.patternsResponse, patterns });
}

async function openBreakdown() {
  renderRoutes(routes, { route: `/runs/${f.RUN_ID}/patterns/TH-001` });
  await userEvent.click(await screen.findByText('How confidence was determined'));
}

describe('PatternDetailPage confidence with participation', () => {
  it('shows the participation factor as a percentage and the respondent count as a number', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => withBreakdown({ participation: 0.6, respondents: 6 })));
    await openBreakdown();
    expect(screen.getByText('Participation').nextElementSibling).toHaveTextContent('60%');
    expect(screen.getByText('Respondents (fewest among the supporting questions)').nextElementSibling).toHaveTextContent('6');
  });

  it('never turns a single respondent into one hundred percent', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => withBreakdown({ participation: 0.1, respondents: 1 })));
    await openBreakdown();
    expect(screen.getByText('Respondents (fewest among the supporting questions)').nextElementSibling).toHaveTextContent(/^1$/);
  });

  it('says the count is not known instead of showing null', async () => {
    server.use(http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => withBreakdown({ participation: 1, respondents: null })));
    await openBreakdown();
    expect(screen.getByText('Respondents (fewest among the supporting questions)').nextElementSibling).toHaveTextContent('Not known');
    expect(screen.queryByText('null')).not.toBeInTheDocument();
  });
});
