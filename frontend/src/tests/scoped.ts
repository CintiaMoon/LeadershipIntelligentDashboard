// Test-only data for answers with a scope (decision D45), in the shape the backend returns. Never imported by production code.
import type { ScoringResult } from '@/types/api';
import { RUN_ID, run } from './fixtures';

export const TEAM_RUN_ID = '33333333-3333-3333-3333-333333333333';
export const SMALL_TEAM_RUN_ID = '44444444-4444-4444-4444-444444444444';

const questionSummary = (questionId: string, respondents: number | null, belowMinimum = false, participation = 'OBSERVED') => ({
  questionId,
  scope: 'MEMBER',
  method: 'MEDIAN',
  respondents,
  participation,
  usedAnswers: respondents ?? 0,
  minimum: 5,
  belowMinimum,
});

export const responses = {
  questions: [questionSummary('Q01', 11), questionSummary('Q02', 11), questionSummary('Q03', 11)],
};

/** The organization run of an assessment scored from member answers: one team with a score, one with too few responses, one unscored. */
export const scopedRun: ScoringResult = {
  ...run,
  scope: 'ORGANIZATION',
  responses,
  teamRuns: [
    { teamId: 'alpha', scoringRunId: TEAM_RUN_ID, scoreStatus: 'VALID', leadershipIntelligenceScore: 71.5, interpretation: 'HEALTHY' },
    { teamId: 'small', scoringRunId: SMALL_TEAM_RUN_ID, scoreStatus: 'INSUFFICIENT_DATA' },
    { teamId: 'tiny', scoreStatus: 'INSUFFICIENT_DATA' },
  ],
};

/** The run of team alpha: the same shape as any run, with its own score and its own response summaries. */
export const teamRun: ScoringResult = {
  ...run,
  scoringRunId: TEAM_RUN_ID,
  scope: 'TEAM',
  teamId: 'alpha',
  organizationScoringRunId: RUN_ID,
  leadershipIntelligenceScore: 71.5,
  responses: { questions: [questionSummary('Q01', 6), questionSummary('Q02', 6), questionSummary('Q03', 6)] },
};

/** A team below the minimum: it has a run, no score, and says which questions had too few responses. */
export const smallTeamRun: ScoringResult = {
  ...teamRun,
  scoringRunId: SMALL_TEAM_RUN_ID,
  teamId: 'small',
  scoreStatus: 'INSUFFICIENT_DATA',
  leadershipIntelligenceScore: undefined,
  interpretation: undefined,
  responses: { questions: [questionSummary('Q01', 3, true), questionSummary('Q02', 3, true)] },
};

/** Member answers arrive as groups: sorted values with a count, or the count alone when the group is too small. */
export const memberAnswersResponse = {
  scoringRunId: RUN_ID,
  answers: [
    { questionId: 'Q01', scope: 'MEMBER', count: 5, values: [1, 2, 3, 4, 5], suppressed: false },
    { questionId: 'Q02', scope: 'MEMBER', count: 3, values: [], suppressed: true },
    { questionId: 'Q03', scope: 'TEAM', teamId: 'alpha', answer: true, declaredRespondents: 8 },
  ],
};
