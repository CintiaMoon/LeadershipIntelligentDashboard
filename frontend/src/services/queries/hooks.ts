import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type AssessmentListParams, type RunListParams, type TrendParams } from '@/services/api/endpoints';
import type { CalculateRequest, CreateTenantRequest, RecalculateRequest } from '@/types/api';
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
