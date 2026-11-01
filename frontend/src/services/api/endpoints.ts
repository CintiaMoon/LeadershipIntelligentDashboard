import type {
  AssessmentPage,
  AssessmentSummary,
  CalculateRequest,
  Comparison,
  Dashboard,
  Explanation,
  RecalculateRequest,
  Recommendations,
  RunAnswers,
  RunCatalog,
  RunDimensions,
  RunImbalances,
  RunPage,
  RunPatterns,
  RunPenalties,
  ScoringResult,
  TenantList,
  Tenant,
  CreateTenantRequest,
  Trends,
  QuestionnaireSummary,
  QuestionnaireDetail,
  QuestionnaireDocument,
  TeamView,
  PersonView,
  CreateTeamRequest,
  CreatePersonRequest,
  UpdatePersonRequest,
  AssessmentRunSummary,
  AssessmentRunDetail,
  StartRunRequest,
  RespondentRun,
  RespondentRunDetail,
  SaveDraftRequest,
} from '@/types/api';
import { apiFetch, type Query } from './client';

const enc = encodeURIComponent;

export interface Paging {
  cursor?: string;
  limit?: number;
}
export interface RunListParams extends Paging {
  organizationId?: string;
  assessmentId?: string;
  from?: string;
  to?: string;
}
export interface AssessmentListParams extends Paging {
  organizationId?: string;
  status?: string;
}
export interface TrendParams {
  templateId?: string;
  from?: string;
  to?: string;
  limit?: number;
}

// One function per backend endpoint (docs/implementation/API_CONTRACT.md). No scoring logic lives here.
export const api = {
  dashboard: (organizationId: string, signal?: AbortSignal) => apiFetch<Dashboard>(`/organizations/${enc(organizationId)}/dashboard`, { signal }),
  trends: (organizationId: string, params: TrendParams = {}, signal?: AbortSignal) =>
    apiFetch<Trends>(`/organizations/${enc(organizationId)}/trends`, { query: params as Query, signal }),

  assessments: (params: AssessmentListParams = {}, signal?: AbortSignal) => apiFetch<AssessmentPage>('/assessments', { query: params as Query, signal }),
  assessment: (id: string, signal?: AbortSignal) => apiFetch<AssessmentSummary>(`/assessments/${enc(id)}`, { signal }),

  runs: (params: RunListParams = {}, signal?: AbortSignal) => apiFetch<RunPage>('/scoring-runs', { query: params as Query, signal }),
  run: (id: string, signal?: AbortSignal) => apiFetch<ScoringResult>(`/scoring-runs/${enc(id)}`, { signal }),
  explanation: (id: string, signal?: AbortSignal) => apiFetch<Explanation>(`/scoring-runs/${enc(id)}/explanation`, { signal }),
  dimensions: (id: string, signal?: AbortSignal) => apiFetch<RunDimensions>(`/scoring-runs/${enc(id)}/dimensions`, { signal }),
  patterns: (id: string, signal?: AbortSignal) => apiFetch<RunPatterns>(`/scoring-runs/${enc(id)}/patterns`, { signal }),
  penalties: (id: string, signal?: AbortSignal) => apiFetch<RunPenalties>(`/scoring-runs/${enc(id)}/penalties`, { signal }),
  imbalances: (id: string, signal?: AbortSignal) => apiFetch<RunImbalances>(`/scoring-runs/${enc(id)}/imbalances`, { signal }),
  recommendations: (id: string, signal?: AbortSignal) => apiFetch<Recommendations>(`/scoring-runs/${enc(id)}/recommendations`, { signal }),
  catalog: (id: string, signal?: AbortSignal) => apiFetch<RunCatalog>(`/scoring-runs/${enc(id)}/catalog`, { signal }),
  answers: (id: string, signal?: AbortSignal) => apiFetch<RunAnswers>(`/scoring-runs/${enc(id)}/answers`, { signal }),
  compare: (baseRunId: string, runId: string, signal?: AbortSignal) => apiFetch<Comparison>('/scoring-runs/compare', { query: { baseRunId, runId }, signal }),

  latestScore: (assessmentId: string, signal?: AbortSignal) => apiFetch<ScoringResult>(`/assessments/${enc(assessmentId)}/score`, { signal }),
  calculate: (request: CalculateRequest, idempotencyKey?: string) =>
    apiFetch<ScoringResult>('/calculate', { method: 'POST', body: request, headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined }),
  score: (assessmentId: string, idempotencyKey?: string) =>
    apiFetch<ScoringResult>(`/assessments/${enc(assessmentId)}/score`, { method: 'POST', headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined }),
  tenants: (signal?: AbortSignal) => apiFetch<TenantList>('/platform/tenants', { signal }),
  createTenant: (request: CreateTenantRequest) => apiFetch<Tenant>('/platform/tenants', { method: 'POST', body: request }),
  // questionnaire library and builder (decisions D47, D50)
  questionnaires: (signal?: AbortSignal) => apiFetch<QuestionnaireSummary[]>('/questionnaires', { signal }),
  questionnaire: (id: string, version?: number, signal?: AbortSignal) => apiFetch<QuestionnaireDetail>(`/questionnaires/${enc(id)}`, { query: { version }, signal }),
  createQuestionnaire: (name: string) => apiFetch<QuestionnaireDetail>('/questionnaires', { method: 'POST', body: { name } }),
  questionnaireFromInitial: (name: string) => apiFetch<QuestionnaireDetail>('/questionnaires/from-initial', { method: 'POST', body: { name } }),
  saveQuestionnaire: (id: string, document: QuestionnaireDocument) => apiFetch<QuestionnaireDetail>(`/questionnaires/${enc(id)}`, { method: 'PUT', body: document }),
  publishQuestionnaire: (id: string) => apiFetch<QuestionnaireDetail>(`/questionnaires/${enc(id)}/publish`, { method: 'POST' }),
  archiveQuestionnaire: (id: string) => apiFetch<QuestionnaireDetail>(`/questionnaires/${enc(id)}/archive`, { method: 'POST' }),
  duplicateQuestionnaire: (id: string) => apiFetch<QuestionnaireDetail>(`/questionnaires/${enc(id)}/duplicate`, { method: 'POST' }),

  // roster and assessment runs (decision D48)
  teams: (organizationId?: string, signal?: AbortSignal) => apiFetch<TeamView[]>('/teams', { query: { organizationId }, signal }),
  createTeam: (request: CreateTeamRequest) => apiFetch<TeamView>('/teams', { method: 'POST', body: request }),
  renameTeam: (teamId: string, name: string) => apiFetch<TeamView>(`/teams/${enc(teamId)}`, { method: 'PUT', body: { name } }),
  people: (teamId?: string, signal?: AbortSignal) => apiFetch<PersonView[]>('/people', { query: { teamId }, signal }),
  createPerson: (request: CreatePersonRequest) => apiFetch<PersonView>('/people', { method: 'POST', body: request }),
  updatePerson: (personId: string, request: UpdatePersonRequest) => apiFetch<PersonView>(`/people/${enc(personId)}`, { method: 'PUT', body: request }),
  assessmentRuns: (params: { organizationId?: string; status?: string } = {}, signal?: AbortSignal) =>
    apiFetch<AssessmentRunSummary[]>('/runs', { query: params as Query, signal }),
  assessmentRun: (id: string, signal?: AbortSignal) => apiFetch<AssessmentRunDetail>(`/runs/${enc(id)}`, { signal }),
  startRun: (request: StartRunRequest) => apiFetch<AssessmentRunDetail>('/runs', { method: 'POST', body: request }),
  closeRun: (id: string) => apiFetch<AssessmentRunDetail>(`/runs/${enc(id)}/close`, { method: 'POST' }),

  // the respondent flow (decision D48)
  myRuns: (signal?: AbortSignal) => apiFetch<RespondentRun[]>('/respond/runs', { signal }),
  myRun: (id: string, signal?: AbortSignal) => apiFetch<RespondentRunDetail>(`/respond/runs/${enc(id)}`, { signal }),
  saveDraft: (id: string, request: SaveDraftRequest) => apiFetch<RespondentRunDetail>(`/respond/runs/${enc(id)}/draft`, { method: 'PUT', body: request }),
  submitAnswers: (id: string) => apiFetch<RespondentRunDetail>(`/respond/runs/${enc(id)}/submit`, { method: 'POST' }),

  recalculate: (assessmentId: string, request?: RecalculateRequest) =>
    apiFetch<ScoringResult>(`/assessments/${enc(assessmentId)}/score/recalculate`, { method: 'POST', body: request }),
};
