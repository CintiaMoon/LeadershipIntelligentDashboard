import { useMemo } from 'react';
import {
  useCatalog,
  useComparison,
  useDashboard,
  useDimensions,
  usePatterns,
  usePenalties,
  useRecommendations,
  useRun,
  useTrends,
} from '@/services/queries/hooks';
import { parseDimensionDetails, parseEffectiveByTarget, parsePatterns, parsePenalties, parseResponses } from '@/types/views';
import { makeNames } from './display';

/**
 * Everything the dashboard shows, from the backend only: the page never computes a scoring value. With a team selected
 * the page shows that team's own run; history, trends and comparison exist for the whole organization only (decision
 * D45), so they are not requested for a team.
 */
export function useDashboardData(organizationId: string | null, templateId: string | null, teamId: string | null = null) {
  const dashboard = useDashboard(organizationId);
  const latestRuns = dashboard.data?.latestPerTemplate ?? [];
  const latest = latestRuns.find((r) => r.templateId === templateId) ?? latestRuns[0];
  const organizationRunId = latest?.scoringRunId;

  const organizationRun = useRun(organizationRunId);
  const teamRuns = organizationRun.data?.teamRuns ?? [];
  const team = teamId ? teamRuns.find((t) => t.teamId === teamId) : undefined;
  const scoped = team !== undefined;
  // a team that could not be scored has no run, so every run query below stays idle for it
  const runId = scoped ? team.scoringRunId : organizationRunId;

  const run = useRun(runId);
  const dimensions = useDimensions(runId);
  const patterns = usePatterns(runId);
  const penalties = usePenalties(runId);
  const recommendations = useRecommendations(runId);
  const catalog = useCatalog(runId);
  const trends = useTrends(latest && !scoped ? organizationId : null, { templateId: latest?.templateId, limit: 12 });
  const comparison = useComparison(!scoped && latest?.comparable ? latest.previousScoringRunId : undefined, runId);

  const parsed = useMemo(() => {
    try {
      return {
        details: parseDimensionDetails(dimensions.data?.dimensions),
        patterns: parsePatterns(patterns.data?.patterns),
        penalties: parsePenalties(penalties.data?.penalties),
        effectiveByTarget: parseEffectiveByTarget(penalties.data?.effectiveByTarget),
        responses: parseResponses(run.data?.responses),
        error: null as Error | null,
      };
    } catch (e) {
      return { details: [], patterns: [], penalties: [], effectiveByTarget: {}, responses: null, error: e as Error };
    }
  }, [dimensions.data, patterns.data, penalties.data, run.data]);

  const names = useMemo(() => makeNames(catalog.data), [catalog.data]);

  return {
    dashboard,
    latestRuns,
    latest,
    run,
    runId,
    organizationRun,
    teamRuns,
    team,
    scoped,
    dimensions,
    patterns,
    penalties,
    recommendations,
    catalog,
    trends,
    comparison,
    parsed,
    names,
  };
}
