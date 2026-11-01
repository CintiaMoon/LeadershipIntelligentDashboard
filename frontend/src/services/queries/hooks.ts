import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type AssessmentListParams, type RunListParams, type TrendParams } from '@/services/api/endpoints';
import type {
  CalculateRequest,
  CreatePersonRequest,
  CreateTenantRequest,
  CreateTeamRequest,
  QuestionnaireDocument,
  RecalculateRequest,
  SaveDraftRequest,
  StartRunRequest,
  UpdatePersonRequest,
} from '@/types/api';
import { queryKeys } from './keys';

// A stored run never changes (history is immutable), so run-scoped reads are cached for a long time.
const RUN_STALE_MS = 5 * 60_000;

export const useDashboard = (org: string | null) =>
  useQuery({ queryKey: queryKeys.dashboard(org ?? ''), queryFn: ({ signal }) => api.dashboard(org as string, signal), enabled: !!org });

export const useTrends = (org: string | null, params: TrendParams = {}) =>
  useQuery({ queryKey: queryKeys.trends(org ?? '', params), queryFn: ({ signal }) => api.trends(org as string, params, signal), enabled: !!org });

export const useAssessments = (params: AssessmentListParams = {}) =>
  useQuery({ queryKey: queryKeys.assessments(params), queryFn: ({ signal }) => api.assessments(params, signal) });

export const useAssessment = (id: string | undefined) =>
  useQuery({ queryKey: queryKeys.assessment(id ?? ''), queryFn: ({ signal }) => api.assessment(id as string, signal), enabled: !!id });

export const useRuns = (params: RunListParams = {}) =>
  useQuery({ queryKey: queryKeys.runs(params), queryFn: ({ signal }) => api.runs(params, signal) });

function runQuery<T>(key: (id: string) => readonly unknown[], fetcher: (id: string, signal?: AbortSignal) => Promise<T>) {
  return (id: string | undefined) =>
    useQuery({ queryKey: key(id ?? ''), queryFn: ({ signal }) => fetcher(id as string, signal), enabled: !!id, staleTime: RUN_STALE_MS });
}

export const useRun = runQuery(queryKeys.run, api.run);
export const useExplanation = runQuery(queryKeys.explanation, api.explanation);
export const useDimensions = runQuery(queryKeys.dimensions, api.dimensions);
export const usePatterns = runQuery(queryKeys.patterns, api.patterns);
export const usePenalties = runQuery(queryKeys.penalties, api.penalties);
export const useImbalances = runQuery(queryKeys.imbalances, api.imbalances);
export const useRecommendations = runQuery(queryKeys.recommendations, api.recommendations);
export const useCatalog = runQuery(queryKeys.catalog, api.catalog);

/** Source answers: only OWNER, ADMIN and ANALYST may read them, so the query is only enabled for them. */
export const useAnswers = (id: string | undefined, allowed: boolean) =>
  useQuery({ queryKey: queryKeys.answers(id ?? ''), queryFn: ({ signal }) => api.answers(id as string, signal), enabled: !!id && allowed, staleTime: RUN_STALE_MS });

export const useComparison = (baseRunId: string | undefined, runId: string | undefined) =>
  useQuery({
    queryKey: queryKeys.compare(baseRunId ?? '', runId ?? ''),
    queryFn: ({ signal }) => api.compare(baseRunId as string, runId as string, signal),
    enabled: !!baseRunId && !!runId,
    staleTime: RUN_STALE_MS,
  });

/** Creating a run changes every list and series that can include it. */
function useInvalidateOnRun() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: queryKeys.all });
}

export function useCalculate() {
  const invalidate = useInvalidateOnRun();
  return useMutation({ mutationFn: (v: { request: CalculateRequest; idempotencyKey?: string }) => api.calculate(v.request, v.idempotencyKey), onSuccess: invalidate });
}

export function useScoreAssessment() {
  const invalidate = useInvalidateOnRun();
  return useMutation({ mutationFn: (v: { assessmentId: string; idempotencyKey?: string }) => api.score(v.assessmentId, v.idempotencyKey), onSuccess: invalidate });
}

/** Every tenant. Only the platform administrator may read it, so the query is enabled only for that role. */
export const useTenants = (allowed: boolean) =>
  useQuery({ queryKey: queryKeys.tenants, queryFn: ({ signal }) => api.tenants(signal), enabled: allowed });

export function useCreateTenant() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateTenantRequest) => api.createTenant(request),
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.tenants }),
  });
}

export function useRecalculate() {
  const invalidate = useInvalidateOnRun();
  return useMutation({ mutationFn: (v: { assessmentId: string; request?: RecalculateRequest }) => api.recalculate(v.assessmentId, v.request), onSuccess: invalidate });
}

// ---------------- questionnaire library and builder (decisions D47, D50) ----------------

export const useQuestionnaires = () => useQuery({ queryKey: queryKeys.questionnaires, queryFn: ({ signal }) => api.questionnaires(signal) });

export const useQuestionnaire = (id: string | undefined, version?: number) =>
  useQuery({ queryKey: queryKeys.questionnaire(id ?? '', version), queryFn: ({ signal }) => api.questionnaire(id as string, version, signal), enabled: !!id });

/** Any change to a questionnaire changes the library list and that questionnaire. */
function useQuestionnaireMutation<V, R extends { id?: string }>(fn: (v: V) => Promise<R>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (detail) => {
      // the response is the questionnaire as it is now, so the page shows it without another round trip
      if (detail.id) client.setQueryData(queryKeys.questionnaire(detail.id), detail);
      return Promise.all([client.invalidateQueries({ queryKey: ['lis', 'questionnaires'] }), client.invalidateQueries({ queryKey: ['lis', 'questionnaire'] })]);
    },
  });
}

export const useCreateQuestionnaire = () => useQuestionnaireMutation((v: { name: string; fromInitial: boolean }) => (v.fromInitial ? api.questionnaireFromInitial(v.name) : api.createQuestionnaire(v.name)));
export const useSaveQuestionnaire = () => useQuestionnaireMutation((v: { id: string; document: QuestionnaireDocument }) => api.saveQuestionnaire(v.id, v.document));
export const usePublishQuestionnaire = () => useQuestionnaireMutation((id: string) => api.publishQuestionnaire(id));
export const useArchiveQuestionnaire = () => useQuestionnaireMutation((id: string) => api.archiveQuestionnaire(id));
export const useDuplicateQuestionnaire = () => useQuestionnaireMutation((id: string) => api.duplicateQuestionnaire(id));

// ---------------- roster and assessment runs (decision D48) ----------------

export const useTeams = (organizationId?: string, enabled = true) =>
  useQuery({ queryKey: queryKeys.teams(organizationId), queryFn: ({ signal }) => api.teams(organizationId, signal), enabled });

export const usePeople = (teamId?: string, enabled = true) =>
  useQuery({ queryKey: queryKeys.people(teamId), queryFn: ({ signal }) => api.people(teamId, signal), enabled });

/** A team change shows in the team list (people counts) and a person change in both lists. */
function useRosterMutation<V, R>(fn: (v: V) => Promise<R>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => Promise.all([client.invalidateQueries({ queryKey: queryKeys.teamsAll }), client.invalidateQueries({ queryKey: queryKeys.peopleAll })]),
  });
}

export const useCreateTeam = () => useRosterMutation((request: CreateTeamRequest) => api.createTeam(request));
export const useRenameTeam = () => useRosterMutation((v: { teamId: string; name: string }) => api.renameTeam(v.teamId, v.name));
export const useCreatePerson = () => useRosterMutation((request: CreatePersonRequest) => api.createPerson(request));
export const useUpdatePerson = () => useRosterMutation((v: { personId: string; request: UpdatePersonRequest }) => api.updatePerson(v.personId, v.request));

export const useAssessmentRuns = (params: { organizationId?: string; status?: string } = {}, enabled = true) =>
  useQuery({ queryKey: queryKeys.assessmentRuns(params), queryFn: ({ signal }) => api.assessmentRuns(params, signal), enabled });

export const useAssessmentRun = (id: string | undefined) =>
  useQuery({ queryKey: queryKeys.assessmentRun(id ?? ''), queryFn: ({ signal }) => api.assessmentRun(id as string, signal), enabled: !!id });

export function useStartRun() {
  const client = useQueryClient();
  return useMutation({ mutationFn: (request: StartRunRequest) => api.startRun(request), onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.assessmentRunsAll }) });
}

/** Closing a run scores it, so every result list and series changes as well. */
export function useCloseRun() {
  const client = useQueryClient();
  return useMutation({ mutationFn: (id: string) => api.closeRun(id), onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.all }) });
}

// ---------------- the respondent flow (decision D48) ----------------

export const useMyRuns = (enabled = true) => useQuery({ queryKey: queryKeys.myRuns, queryFn: ({ signal }) => api.myRuns(signal), enabled });

export const useMyRun = (id: string | undefined) =>
  useQuery({ queryKey: queryKeys.myRun(id ?? ''), queryFn: ({ signal }) => api.myRun(id as string, signal), enabled: !!id });

export function useSaveDraft() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; request: SaveDraftRequest }) => api.saveDraft(v.id, v.request),
    onSuccess: (detail, v) => {
      client.setQueryData(queryKeys.myRun(v.id), detail);
      return client.invalidateQueries({ queryKey: queryKeys.myRuns });
    },
  });
}

export function useSubmitAnswers() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.submitAnswers(id),
    onSuccess: (detail, id) => {
      client.setQueryData(queryKeys.myRun(id), detail);
      return client.invalidateQueries({ queryKey: queryKeys.myRuns });
    },
  });
}
