import type { AssessmentListParams, RunListParams, TrendParams } from '@/services/api/endpoints';

// Every cached response is keyed by what it was read for. Scoring results are never copied into client state:
// TanStack Query is the only cache, and it is invalidated when a run is created.
export const queryKeys = {
  all: ['lis'] as const,
  dashboard: (org: string) => ['lis', 'dashboard', org] as const,
  trends: (org: string, params: TrendParams) => ['lis', 'trends', org, params] as const,
  assessments: (params: AssessmentListParams) => ['lis', 'assessments', params] as const,
  assessment: (id: string) => ['lis', 'assessment', id] as const,
  runs: (params: RunListParams) => ['lis', 'runs', params] as const,
  run: (id: string) => ['lis', 'run', id] as const,
  explanation: (id: string) => ['lis', 'run', id, 'explanation'] as const,
  dimensions: (id: string) => ['lis', 'run', id, 'dimensions'] as const,
  patterns: (id: string) => ['lis', 'run', id, 'patterns'] as const,
  penalties: (id: string) => ['lis', 'run', id, 'penalties'] as const,
  imbalances: (id: string) => ['lis', 'run', id, 'imbalances'] as const,
  recommendations: (id: string) => ['lis', 'run', id, 'recommendations'] as const,
  answers: (id: string) => ['lis', 'run', id, 'answers'] as const,
  catalog: (id: string) => ['lis', 'run', id, 'catalog'] as const,
  tenants: ['lis', 'platform', 'tenants'] as const,
  compare: (base: string, run: string) => ['lis', 'compare', base, run] as const,
  questionnaires: ['lis', 'questionnaires'] as const,
  questionnaire: (id: string, version?: number) => ['lis', 'questionnaire', id, version ?? 'current'] as const,
  teams: (org?: string) => ['lis', 'teams', org ?? 'all'] as const,
  teamsAll: ['lis', 'teams'] as const,
  people: (team?: string) => ['lis', 'people', team ?? 'all'] as const,
  peopleAll: ['lis', 'people'] as const,
  assessmentRuns: (params: { organizationId?: string; status?: string }) => ['lis', 'assessment-runs', params] as const,
  assessmentRunsAll: ['lis', 'assessment-runs'] as const,
  assessmentRun: (id: string) => ['lis', 'assessment-run', id] as const,
  myRuns: ['lis', 'respond', 'runs'] as const,
  myRun: (id: string) => ['lis', 'respond', 'run', id] as const,
};
